#!/usr/bin/env python3
import sys
import argparse
import csv
import requests


try:
    import jsonpickle
except ImportError as e:
    print('Please install jsonpickle: python -m pip install --user "jsonpickle"')
    sys.exit(1)


class Measurement:
    def __init__(self, uom, value):
        self.uom = uom
        self.value = value


class Event:
    def __init__(self, event_type, account, instance, timestamp, expiration, role, sla, uom, value, service_type):
        self.event_source = "prometheus"
        self.event_type = "snapshot_" + event_type
        self.account_number = account
        self.instance_id = instance
        self.timestamp = timestamp
        self.expiration = expiration
        self.display_name = instance
        self.measurements = [ Measurement(uom, value)]
        self.role = role
        self.sla = sla
        self.service_type = service_type


def import_events(url, csv_path, dry_run):
    events = parse_csv(csv_path)
    print("Found {num} records".format(num=len(events)))
    return post_events(url, events, dry_run=dry_run)


def parse_csv(csv_path):
    with open(csv_path, mode='r') as csv_file:
        reader = csv.DictReader(csv_file)
        events = []
        for row in reader:
            events.append(Event(**row))
        return events


def post_events(url, events, dry_run=False):
    payload = {
        'type': 'exec',
        'mbean': 'org.candlepin.subscriptions.jmx:name=eventJmxBean,type=EventJmxBean',
        'operation': 'saveEvents(java.lang.String)',
        'arguments': [jsonpickle.encode(events, unpicklable=False)]
    }

    if dry_run:
        print("Dry run! No request will be made!")
        return jsonpickle.encode(payload, unpicklable=False)
    else:
        return requests.post(url, data=jsonpickle.encode(payload, unpicklable=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description="""Insert Events from CSV over JMX.
        The CSV must have a header containing the following rows:
        event_type, account, instance, timestamp, expiration, role, sla, uom, value, service_type
    """)
    parser.add_argument('--host', default='localhost', help='Jolokia host')
    parser.add_argument('--port', default='9000', help='Jolokia port')
    parser.add_argument('--file', help='Path to CSV file', required=True)
    parser.add_argument('--dry-run', action='store_true', help="Only collect the events from the file and do not send.")

    args = parser.parse_args()
    jolokia_url = "http://{host}:{port}/hawtio/jolokia/".format(host = args.host, port = args.port)
    resp = import_events(jolokia_url, args.file, args.dry_run)
    if hasattr(resp, 'status_code') and resp.status_code == 200:
        resp_data = jsonpickle.decode(resp.text)
        if 'error' in resp_data:
            print("Error creating events! {error}".format(error=resp_data['error']))
        else:
            print("Successfully created events.")
    else:
        print(resp)
