import { createMemoryCredentialStore } from "./auth/credentialStore";
import { secureStorage } from "./auth/secureStorage";

export const credentialStore = createMemoryCredentialStore(secureStorage);
