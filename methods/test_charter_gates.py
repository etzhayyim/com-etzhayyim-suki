#!/usr/bin/env python3
"""suki 鋤 — structural charter-gate conformance tests over the central lexicons.

ADR-2605261500. suki is the open-hardware farm-tractor actor (mitsuho's mfg-side sibling).
Its constitutional gates G1–G14 are operationalized at the schema layer: G9/G10 Right-to-Repair
(open bootloader + open CAN + no dealer lockout + open parts catalogue), G7 fuel transition
(no fossil-only R2+ powertrain), G8 emissions audit, G4 witness quorum, N11 no surveillance
hardware, G3 modular implement (no DRM / multi-vendor), G12 KPI cap (≤SAE L3, speed/axle caps).
This is the first executable check that pins those gates (suki had NO dedicated charter-gate
test — only a passing reference in an e7m-sim USD-scene test).

Standalone-runnable (`python3 test_charter_gates.py`) AND pytest-compatible; pure stdlib.
"""
from __future__ import annotations

import json
import os

# G7: the only representable fuels — no fossil-only powertrain at R2+.
FUEL_SET = {
    "B100-biodiesel-R0-R1", "diesel-LFP-hybrid-R0-R1",
    "LFP-electric-R2-plus", "H2-fuel-cell-R2-plus", "methanol-fuel-cell-R2-plus",
}
RECORDS = (
    "cabAttestation.json", "chassisAttestation.json", "electricalEcuAttestation.json",
    "emissionsAuditRecord.json", "fieldTestRecord.json", "hitchPtoAttestation.json",
    "paintAttestation.json", "powertrainAttestation.json",
)


def _lex_dir():
    d = os.path.dirname(os.path.abspath(__file__))
    while d != "/":
        cand = os.path.join(d, "00-contracts", "lexicons", "com", "etzhayyim", "suki")
        if os.path.isdir(cand):
            return cand
        d = os.path.dirname(d)
    raise FileNotFoundError("could not locate 00-contracts/lexicons/com/etzhayyim/suki")


LEX = _lex_dir()


def _load(name):
    with open(os.path.join(LEX, name)) as f:
        return json.load(f)


def _required_union(doc):
    s = set()

    def walk(o):
        if isinstance(o, dict):
            r = o.get("required")
            if isinstance(r, list):
                s.update(r)
            for v in o.values():
                walk(v)
        elif isinstance(o, list):
            for v in o:
                walk(v)
    walk(doc)
    return s


def _known(doc, field):
    out = set()

    def walk(o, parent=None):
        if isinstance(o, dict):
            if "knownValues" in o and parent == field:
                out.update(o["knownValues"])
            for k, v in o.items():
                walk(v, k)
        elif isinstance(o, list):
            for v in o:
                walk(v, parent)
    walk(doc)
    return out


# ── G9/G10 Right-to-Repair — open bootloader + open CAN + no dealer lockout ──
def test_g10_right_to_repair_fields():
    doc = _load("electricalEcuAttestation.json")
    req = _required_union(doc)
    for field in ("ecuReflashNotManufacturerGated", "noDealerLockoutAttest",
                  "replacementPartsCataloguedOpen", "diagnosticCodesOpenInterpretable",
                  "g10RtrInvariantVerify", "unlockStateAtShipDefault"):
        assert field in req, f"G10 RTR: electricalEcuAttestation must require {field}"


def test_g9_bootloader_and_canbus_are_open():
    doc = _load("electricalEcuAttestation.json")
    boots = _known(doc, "name")
    assert boots and all("open" in b.lower() for b in boots), f"G9: bootloader options must all be open, got {boots}"
    can = _known(doc, "canBusProtocol")
    assert can == {"ISOBUS-ISO-11783-open"}, f"G9: CAN bus must be the open ISOBUS only (no proprietary CAN), got {can}"


# ── G7 — fuel transition: no fossil-only R2+ powertrain ──
def test_g7_fuel_set_no_fossil_only_r2():
    for name in ("powertrainAttestation.json", "emissionsAuditRecord.json"):
        doc = _load(name)
        field = "fuelType" if name.startswith("powertrain") else "currentFuelType"
        fuels = _known(doc, field)
        assert fuels == FUEL_SET, f"G7: {name} {field} must be exactly {FUEL_SET}, got {fuels}"
    assert "g7PhaseGateVerify" in _required_union(_load("powertrainAttestation.json")), "G7: powertrain must verify the fuel phase gate"


# ── G8 — emissions audit (NOx + PM + ISO 8178 + jurisdiction certs) ──
def test_g8_emissions_audit_required():
    req = _required_union(_load("emissionsAuditRecord.json"))
    for field in ("noxGramPerKwh", "particulateMatterGramPerKwh", "iso8178NrscPass",
                  "iso8178NrtcPass", "jurisdictionCertifications"):
        assert field in req, f"G8: emissionsAuditRecord must require {field}"


# ── G4 — witness quorum (≥2 robot DIDs) on every attestation ──
def test_g4_witness_quorum_on_all_records():
    for name in RECORDS:
        assert "witnessRobotDids" in _required_union(_load(name)), f"G4: {name} must require witnessRobotDids"


# ── N11 — no surveillance hardware in the cab ──
def test_n11_no_surveillance_hardware():
    assert "noSurveillanceHardwareVerified" in _required_union(_load("cabAttestation.json")), \
        "N11: cabAttestation must verify no surveillance hardware (no always-on camera / telemetry monetization)"


# ── G3 — modular implement: no DRM, multi-vendor, open ISOBUS detection ──
def test_g3_implement_no_drm_multivendor():
    doc = _load("hitchPtoAttestation.json")
    req = _required_union(doc)
    for field in ("noDrmDetectionVerified", "multiVendorCompatVerify"):
        assert field in req, f"G3: hitchPtoAttestation must require {field}"
    assert _known(doc, "implementDetectionProtocol") == {"ISOBUS-ISO-11783-open"}, "G3: implement detection must be open ISOBUS"
    assert _known(doc, "hitchCategory") == {"Cat-I", "Cat-II", "Cat-III"}, "G3: standard hitch categories only"


# ── G12 — KPI cap (SAE autonomy level + speed/axle caps verified) ──
def test_g12_kpi_cap_verified():
    req = _required_union(_load("fieldTestRecord.json"))
    for field in ("g12KpiCapVerify", "saeAutonomyLevel", "maxRoadSpeedKmh", "maxFieldSpeedKmh", "maxAxleLoadT"):
        assert field in req, f"G12: fieldTestRecord must require {field}"


def _run():
    fns = [v for k, v in sorted(globals().items()) if k.startswith("test_") and callable(v)]
    for fn in fns:
        fn()
    print(f"suki/charter_gates: {len(fns)} tests passed (lex dir: {os.path.relpath(LEX)})")


if __name__ == "__main__":
    _run()
