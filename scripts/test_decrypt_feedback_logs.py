import base64
import hashlib
import json
import unittest

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from scripts.decrypt_feedback_logs import ALGORITHM, FORMAT, decrypt_log


class DecryptFeedbackLogsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.private = rsa.generate_private_key(public_exponent=65537, key_size=3072)
        cls.private_pem = cls.private.private_bytes(
            serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()
        )

    def envelope(self):
        public_der = self.private.public_key().public_bytes(
            serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
        )
        key_id = hashlib.sha256(public_der).hexdigest()[:16]
        key = AESGCM.generate_key(bit_length=256)
        nonce = bytes(range(12))
        aad = f"{FORMAT}|{ALGORITHM}|{key_id}".encode()
        encode = lambda value: base64.b64encode(value).decode()
        return {
            "format": FORMAT, "algorithm": ALGORITHM, "keyId": key_id,
            "nonce": encode(nonce),
            "ciphertext": encode(AESGCM(key).encrypt(nonce, "测试日志".encode(), aad)),
            "wrappedKey": encode(self.private.public_key().encrypt(key, padding.OAEP(
                mgf=padding.MGF1(hashes.SHA256()), algorithm=hashes.SHA256(), label=None
            ))),
        }

    def test_correct_key_decrypts(self):
        self.assertEqual("测试日志".encode(), decrypt_log(json.dumps(self.envelope()).encode(), self.private_pem))

    def test_corrupted_payload_and_modified_header_are_rejected(self):
        for field in ("format", "algorithm", "keyId", "ciphertext", "wrappedKey", "nonce"):
            with self.subTest(field=field):
                envelope = self.envelope()
                envelope[field] = "modified"
                with self.assertRaises((ValueError, InvalidTag)):
                    decrypt_log(json.dumps(envelope).encode(), self.private_pem)

    def test_wrong_private_key_is_rejected(self):
        wrong = rsa.generate_private_key(public_exponent=65537, key_size=3072)
        wrong_pem = wrong.private_bytes(
            serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()
        )
        with self.assertRaisesRegex(ValueError, "does not match"):
            decrypt_log(json.dumps(self.envelope()).encode(), wrong_pem)


if __name__ == "__main__":
    unittest.main()
