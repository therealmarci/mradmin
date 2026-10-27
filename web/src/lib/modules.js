// The web part of the modules (MRchunks, MRsleep, MRpvp ...) - the same four calls for every plugin, see
// ModuleRoutes.java. Permissions: me.may.<id> to see a module, me.may.<id><Key> for its actions.
import { api } from './api.js';

/** Data of a module, e.g. moduleData('mrchunks', 'loader', { id: 3 }). */
export function moduleData(id, request, args = {}) {
  const query = new URLSearchParams(Object.entries(args).map(([k, v]) => [k, String(v)])).toString();
  return api('GET', `/modules/${id}/data/${request}` + (query ? '?' + query : ''));
}

/** An action of a module: args.action names it, e.g. moduleAction('mrpvp', { action: 'stop', id: 12 }). */
export function moduleAction(id, args) {
  const { action, ...rest } = args;
  return api('POST', `/modules/${id}/actions/${action}`, rest);
}

/** A module's part of a player's page. */
export function playerPart(uuid, id) {
  return api('GET', `/players/${uuid}/modules/${id}`);
}

/** An action of a module on a player: args.action names it. */
export function playerAction(uuid, id, args) {
  const { action, ...rest } = args;
  return api('POST', `/players/${uuid}/modules/${id}/${action}`, rest);
}
