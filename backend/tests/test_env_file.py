import os

from app import config


def test_env_file_sets_missing_values_only(tmp_path, monkeypatch):
    env = tmp_path / ".env"
    env.write_text(
        "# comment\nCOZY_A=one\nCOZY_B='quoted'\nCOZY_C=\nCOZY_D=from-file\n",
        encoding="utf-8",
    )
    for key in ("COZY_A", "COZY_B", "COZY_C"):
        monkeypatch.delenv(key, raising=False)
    monkeypatch.setenv("COZY_D", "from-env")
    monkeypatch.delenv("TESTING", raising=False)

    config._load_env_file(env)

    assert os.environ["COZY_A"] == "one"
    assert os.environ["COZY_B"] == "quoted"
    assert "COZY_C" not in os.environ  # empty: keep the built-in default
    assert os.environ["COZY_D"] == "from-env"  # real environment wins
    for key in ("COZY_A", "COZY_B"):
        monkeypatch.delenv(key)


def test_env_file_ignored_in_tests(tmp_path, monkeypatch):
    env = tmp_path / ".env"
    env.write_text("COZY_E=x\n", encoding="utf-8")
    monkeypatch.delenv("COZY_E", raising=False)
    monkeypatch.setenv("TESTING", "1")

    config._load_env_file(env)

    assert "COZY_E" not in os.environ
