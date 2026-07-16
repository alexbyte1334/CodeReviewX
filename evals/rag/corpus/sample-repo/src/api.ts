export function createUser(input) { return repository.save(input); }
export function loadUser(id) { return repository.findById(id); }
