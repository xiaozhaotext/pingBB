const sandbox = import.meta.env.VITE_SANDBOX_MODE === 'true'

export const memberStoreId = sandbox ? 'sandbox-member' : 'member'
export const tokenStorageKey = sandbox ? 'sandbox-token' : 'token'
export const usernameStorageKey = sandbox ? 'sandbox-username' : 'username'
export const passwordStorageKey = sandbox ? 'sandbox-password' : 'password'
