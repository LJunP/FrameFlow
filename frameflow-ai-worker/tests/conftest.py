import json
import sys
from pathlib import Path

import pytest

REPO_ROOT = Path(__file__).resolve().parents[2]
PROFILE = REPO_ROOT / "experiments/fixtures/profile-ECOMMERCE_SHORT_AD_V1.json"
DATASET = REPO_ROOT / "experiments/fixtures/generated"


@pytest.fixture(scope="session")
def profile() -> dict:
    return json.loads(PROFILE.read_text(encoding="utf-8"))


@pytest.fixture(scope="session")
def profile_digest() -> str:
    import hashlib
    return hashlib.sha256(PROFILE.read_bytes()).hexdigest()[:16]


@pytest.fixture(scope="session")
def dataset() -> Path:
    return DATASET
