# Keep readable crash traces.
-keepattributes SourceFile,LineNumberTable
# Google sign-in credential types are created from data received at run time.
-keep class com.google.android.libraries.identity.googleid.** { *; }
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
