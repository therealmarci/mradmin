// Passkeys (WebAuthn) in the browser: the server sends options as JSON with base64url fields, the browser API wants
// binary data - newer browsers convert themselves (parse...FromJSON / toJSON), older ones get the same done here.

export const supported = typeof window !== 'undefined' && !!window.PublicKeyCredential && !!navigator.credentials;

const fromB64 = (s) => {
  const b64 = s.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(s.length / 4) * 4, '=');
  return Uint8Array.from(atob(b64), (c) => c.charCodeAt(0)).buffer;
};

const toB64 = (buffer) => {
  const bytes = new Uint8Array(buffer);
  let s = '';
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
};

function creationOptions(json) {
  if (PublicKeyCredential.parseCreationOptionsFromJSON) return PublicKeyCredential.parseCreationOptionsFromJSON(json);
  return {
    ...json,
    challenge: fromB64(json.challenge),
    user: { ...json.user, id: fromB64(json.user.id) },
    excludeCredentials: (json.excludeCredentials ?? []).map((c) => ({ ...c, id: fromB64(c.id) })),
  };
}

function requestOptions(json) {
  if (PublicKeyCredential.parseRequestOptionsFromJSON) return PublicKeyCredential.parseRequestOptionsFromJSON(json);
  return {
    ...json,
    challenge: fromB64(json.challenge),
    allowCredentials: (json.allowCredentials ?? []).map((c) => ({ ...c, id: fromB64(c.id) })),
  };
}

function credentialJson(credential) {
  if (credential.toJSON) {
    try {
      return credential.toJSON();
    } catch {
      // some browsers have toJSON but fail for some authenticators - convert by hand
    }
  }
  const r = credential.response;
  const response = { clientDataJSON: toB64(r.clientDataJSON) };
  if (r.attestationObject) {
    response.attestationObject = toB64(r.attestationObject);
    if (r.getTransports) response.transports = r.getTransports();
  } else {
    response.authenticatorData = toB64(r.authenticatorData);
    response.signature = toB64(r.signature);
    if (r.userHandle) response.userHandle = toB64(r.userHandle);
  }
  return {
    type: credential.type,
    id: credential.id,
    rawId: toB64(credential.rawId),
    authenticatorAttachment: credential.authenticatorAttachment ?? undefined,
    response,
    clientExtensionResults: credential.getClientExtensionResults?.() ?? {},
  };
}

/** Creates a passkey with the server's options ({publicKey: ...}); returns the answer for the server. */
export async function create(options) {
  const credential = await navigator.credentials.create({ publicKey: creationOptions(options.publicKey) });
  return credentialJson(credential);
}

/** Signs a sign-in challenge with a passkey of the browser's choice. */
export async function get(options) {
  const credential = await navigator.credentials.get({ publicKey: requestOptions(options.publicKey) });
  return credentialJson(credential);
}

/** "NotAllowedError" = the person cancelled or the time ran out - no error message needed. */
export const cancelled = (e) => e?.name === 'NotAllowedError' || e?.name === 'AbortError';

/**
 * Confirms a sensitive step with one of the own passkeys (instead of a 2FA code): returns what the server expects
 * in the body ({request, credential}).
 */
export async function proof(api) {
  const start = await api('POST', '/confirm/passkey');
  const credential = await get(start.options);
  return { request: start.request, credential };
}
