#!/usr/bin/env python3
"""Offline JSON protocol checks. Does not require network or 3rd-party modules."""
from pathlib import Path
import json
import uuid

root = Path(__file__).resolve().parent.parent
schema = json.loads((root / 'protocol/pet-schema-v1.json').read_text(encoding='utf-8'))
sample = json.loads((root / 'protocol/examples/pet-sample.json').read_text(encoding='utf-8'))
quest = json.loads((root / 'protocol/examples/quest-result-v1.json').read_text(encoding='utf-8'))

assert schema['properties']['glyph']['properties']['pixels']['pattern'] == '^[01]{169}$'
assert set(sample) == {'schemaVersion', 'source', 'pet', 'glyph'}
assert sample['schemaVersion'] == 1 and sample['source'] == 'glyph-life'
assert sample['glyph']['width'] == sample['glyph']['height'] == 13
assert len(sample['glyph']['pixels']) == 169 and set(sample['glyph']['pixels']) <= {'0', '1'}
assert str(uuid.UUID(sample['pet']['id'])) == sample['pet']['id']
assert 0 <= sample['pet']['experience'] < 100
assert 0 <= sample['pet']['happiness'] <= 100 and 0 <= sample['pet']['energy'] <= 100
assert sample['pet']['level'] >= 1

assert quest['schemaVersion'] == 1 and quest['source'] == 'monster-quest'
assert str(uuid.UUID(quest['petId'])) == sample['pet']['id']
assert str(uuid.UUID(quest['eventId'])) == quest['eventId']
assert 0 <= quest['experience'] <= 100 and -20 <= quest['happiness'] <= 20
print('Pet and quest-result JSON v1 samples: PASS')
