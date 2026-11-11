#!/usr/bin/env python3
"""Offline structural validation of Glyph Life protocol example (stdlib only)."""
from pathlib import Path
import json
import uuid

ROOT = Path(__file__).resolve().parent.parent
schema = json.loads((ROOT / 'protocol/pet-schema-v1.json').read_text(encoding='utf-8'))
sample = json.loads((ROOT / 'protocol/examples/pet-sample.json').read_text(encoding='utf-8'))
assert schema['properties']['glyph']['properties']['pixels']['pattern'] == '^[01]{169}$'
assert set(sample) == {'schemaVersion','source','pet','glyph'}
assert sample['schemaVersion'] == 1 and sample['source'] == 'glyph-life'
assert sample['glyph']['width'] == sample['glyph']['height'] == 13
assert len(sample['glyph']['pixels']) == 169
assert set(sample['glyph']['pixels']) <= {'0','1'}
assert str(uuid.UUID(sample['pet']['id'])) == sample['pet']['id']
assert 0 <= sample['pet']['experience'] < 100
assert 0 <= sample['pet']['happiness'] <= 100
assert 0 <= sample['pet']['energy'] <= 100
assert sample['pet']['level'] >= 1
print('Protocol v1 sample: PASS')
