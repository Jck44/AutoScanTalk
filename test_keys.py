import plistlib
import os

with open('gotalknow_extracted/Stefanie-2023-02-25.gotalk-book/PageData.plist', 'rb') as f:
    page_data = plistlib.load(f)

# Collect all unique keys across all buttons in all pages to be sure
keys = set()
for page_id, page in page_data.items():
    if not isinstance(page, dict) or 'Buttons' not in page: continue
    for b_idx, b_data in page['Buttons'].items():
        if isinstance(b_data, dict):
            for k, v in b_data.items():
                if k not in keys:
                    keys.add(k)
                    print(f"Key: {k}, Example Value: {v}")

