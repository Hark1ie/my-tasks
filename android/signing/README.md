# Signing key

`release.keystore` signs every build so that new versions install over old ones.
It is stored here, with its password in `signing.properties`, because this is a
private, single-owner repository and the app is installed by hand.

Keep this repository private. If it is ever made public, create a new key first
(the app then has to be uninstalled and reinstalled once).
