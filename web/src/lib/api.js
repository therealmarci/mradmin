// Every call goes to MRadmin's JSON API. The header X-MRadmin marks requests from this interface - MRadmin refuses
// changes without it, so other websites cannot trigger actions with your sign-in.

export class ApiError extends Error {
  constructor(status, key) {
    super(key);
    this.status = status;
    this.key = key;
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
    throw new ApiError(res.status, data?.error ?? 'server');
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
