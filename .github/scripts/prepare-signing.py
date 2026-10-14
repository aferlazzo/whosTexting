"""Prepare private Android signing material without logging secrets."""
import base64
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile

EXPECTED_CERTIFICATE = "0ddca6420e85894472899b0f531a6a7e718378d60e0ce8063ffc8dfd3058c9ca"

def main():
    raw = os.environ.get("WHOS_TEXTING_SIGNING_JSON")
    if not raw:
        raise SystemExit("Missing WHOS_TEXTING_SIGNING_JSON repository secret. Refusing to generate a replacement signing key.")
    try:
        config = json.loads(raw)
        key = base64.b64decode(config["keystore_base64"], validate=True)
        for name in ("store_password", "key_password", "key_alias"):
            if not isinstance(config[name], str) or not config[name] or "\n" in config[name] or "\r" in config[name]:
                raise ValueError("Invalid signing field")
    except (ValueError, KeyError, TypeError):
        raise SystemExit("Invalid signing secret. Secret contents were not logged.")
    folder = Path(tempfile.mkdtemp(prefix="whos-texting-signing-"))
    keystore = folder / "release.p12"
    keystore.write_bytes(key)
    keystore.chmod(0o600)
    password_file = folder / "password"
    password_file.write_text(config["store_password"])
    password_file.chmod(0o600)
    certificate = folder / "certificate.der"
    result = subprocess.run(["keytool", "-exportcert", "-keystore", str(keystore),
        "-alias", config["key_alias"], "-storepass:file", str(password_file),
        "-file", str(certificate)], capture_output=True)
    password_file.unlink()
    if result.returncode:
        raise SystemExit("Unable to open signing keystore. Secret contents were not logged.")
    fingerprint = hashlib.sha256(certificate.read_bytes()).hexdigest()
    if fingerprint != EXPECTED_CERTIFICATE:
        raise SystemExit("Signing certificate mismatch. Refusing to publish an incompatible APK.")
    environment = {
        "WT_STORE_FILE": str(keystore),
        "WT_STORE_PASSWORD": config["store_password"],
        "WT_KEY_PASSWORD": config["key_password"],
        "WT_KEY_ALIAS": config["key_alias"],
    }
    github_env = os.environ["GITHUB_ENV"]
    with open(github_env, "a") as output:
        for name, value in environment.items():
            output.write(f"{name}={value}\n")
    print("Permanent signing certificate verified.")

if __name__ == "__main__":
    main()
