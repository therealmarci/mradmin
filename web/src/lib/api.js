// Every call goes to MRadmin's JSON API. The header X-MRadmin marks requests from this interface - MRadmin refuses
// changes without it, so other websites cannot trigger actions with your sign-in.

export class ApiError extends Error {
  constructor(status, key, text) {
    super(key);
    this.status = status;
    this.key = key;
    // A reason a plugin already wrote in the page's language (player area), shown as it is.
    this.text = text;
    // A number that belongs to the error (e.g. a wait in ms or a maximum length).
    this.value = undefined;
  }
}

export async function api(method, path, body) {
  let res;
  try {
    res = await fetch('/api' + path, {
      method,
      headers: { 'Content-Type': 'application/json', 'X-MRadmin': '1' },
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'same-origin',
    });
  } catch {
    throw new ApiError(0, 'offline');
  }
  let data = null;
  try {
    data = await res.json();
  } catch {
    // empty answer
  }
  if (!res.ok) {
    if (res.status === 401) {
      signedOut();
    }
    const error = new ApiError(res.status, data?.error ?? 'server', data?.message);
    error.value = data?.value;
    throw error;
  }
  return data;
}

let onSignedOut = () => {};
export function whenSignedOut(callback) {
  onSignedOut = callback;
}
function signedOut() {
  onSignedOut();
}
