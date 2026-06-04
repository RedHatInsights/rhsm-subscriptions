#!/usr/bin/env python3
from packaging import version
import requests
import sys

vulnerabilities_dict = [
    {
        'module': 'python3-requests',
        'currentVersion': version.parse(requests.__version__),
        'requiredVersion': version.parse('2.32.0'),
        'vulnerability': 'CVE-2024-35195'
    }
]

def check_vulnerabilities():
    found = 0
    for item in vulnerabilities_dict:
        if item['currentVersion'] < item['requiredVersion']:
            print(f"You are using {item['module']}:{item['currentVersion']} which has the following vulnerability {item['vulnerability']}. Upgrade {item['module']} to {item['requiredVersion']} or later.")
            found = found + 1
    if found > 0:
        print("Use 'pip install --upgrade <name of the module>' to fix the vulnerabilities.")
        sys.exit(1)
