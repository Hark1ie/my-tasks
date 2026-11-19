/**
 * Google "Desktop app" OAuth client for signing in from the PC app. It is
 * created once in the Google Cloud console of the Firebase project
 * (my-tasks-32155). For desktop clients Google treats the secret as public:
 * it is shipped inside the app, and PKCE protects the sign-in.
 *
 * The values are kept as repository secrets (DESKTOP_CLIENT_ID and
 * DESKTOP_CLIENT_SECRET) and written in here by the Windows build, because
 * GitHub refuses pushes that contain them.
 */
export const DESKTOP_CLIENT_ID = "";
export const DESKTOP_CLIENT_SECRET = "";
