Database Population Script
==========================

This script is designed to populate the `rhsm-subscriptions` database with random data for offerings, subscriptions, and related entities. It supports a dry-run mode for SQL statement preview and provides an option to delete all existing data before insertion.


`generate_subscription_data.py`
```python
#!/usr/bin/python3

import argparse
import itertools
import random
import string
from contextlib import closing

import psycopg2
from psycopg2 import extras

DB_CONFIG = {
    'dbname': 'rhsm-subscriptions',
    'user': 'rhsm-subscriptions',
    'password': 'rhsm-subscriptions',
    'host': 'localhost',
    'port': '5432'
}
START_DATE = "2023-09-25 08:25:22.797423 +00:00"
END_DATE = "2024-09-25 08:25:22.000000 +00:00"
VARIABLE_VALUES = {
    "sla": ["Premium", "Standard"],
    "usage": ["Production", "Development"],
    "product_id": ["openshift-dedicated-metrics", "rosa"],
    "billing_provider": ["aws", "gcp"],
    "measurement_type": ["PHYSICAL", "VIRTUAL"],
    "metric_id": ["Instance-hours", "Cores"]
}


def generate_random_string(letters, length):
    return ''.join(random.choices(letters, k=length))


def generate_sku():
    return 'MW' + generate_random_string(string.digits, 5)


def generate_org_id():
    return generate_random_string(string.digits, random.randint(5, 8))


def generate_generic_id(letters, length):
    return generate_random_string(letters, length)


def format_value(value):
    if isinstance(value, str):
        return f"'{value.replace("'", "''")}'"  # Escape single quotes in SQL string
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return 'TRUE' if value else 'FALSE'
    return str(value)


def batch_insert(cursor, query, data, dry_run=False):
    if dry_run:
        for record in data:
            formatted_values = tuple(format_value(value) for value in record)
            print(f'{query % formatted_values};')
    else:
        extras.execute_batch(cursor, query, data)


def main(dry_run, delete_all):
    if delete_all:
        with closing(psycopg2.connect(**DB_CONFIG)) as conn:
            with conn.cursor() as cursor:
                delete_all_records(cursor, dry_run)
                conn.commit()
    else:
        permutations = list(itertools.product(*VARIABLE_VALUES.values()))

        if not dry_run:
            with closing(psycopg2.connect(**DB_CONFIG)) as conn:
                with conn.cursor() as cursor:
                    process_data(permutations, cursor, dry_run)
                    conn.commit()
        else:
            process_data(permutations, None, dry_run)


def process_data(permutations, cursor, dry_run=False):
    offering_data = []
    subscription_data = []
    subscription_measurements_data = []
    subscription_product_ids_data = []

    for perm in permutations:
        sku = generate_sku()
        org_id = generate_org_id()
        subscription_id = generate_generic_id(string.ascii_uppercase + string.digits, 8)
        sla, usage, product_id, billing_provider, measurement_type, metric_id = perm
        billing_provider_id = generate_generic_id(string.ascii_letters + string.digits, 32)
        billing_account_id = generate_generic_id(string.digits, 12)

        offering_data.append((sku, "OpenShift Dedicated", "OpenShift Enterprise", 0, 0, 0, 0, "osd", sla, usage,
                              "Red Hat OpenShift Service on AWS Hosted Control Planes (Hourly)", False, None, None))
        subscription_data.append((sku, org_id, subscription_id, 1, START_DATE, END_DATE, billing_provider_id,
                                  subscription_id, billing_provider, billing_account_id))
        subscription_measurements_data.append((subscription_id, START_DATE, metric_id, measurement_type, 5))
        subscription_product_ids_data.append((product_id, subscription_id, START_DATE))

    insert_query_offering = "INSERT INTO offering VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)"
    insert_query_subscription = "INSERT INTO subscription VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)"
    insert_query_measurements = "INSERT INTO subscription_measurements VALUES (%s, %s, %s, %s, %s)"
    insert_query_product_ids = "INSERT INTO subscription_product_ids VALUES (%s, %s, %s)"

    batch_insert(cursor, insert_query_offering, offering_data, dry_run)
    batch_insert(cursor, insert_query_subscription, subscription_data, dry_run)
    batch_insert(cursor, insert_query_measurements, subscription_measurements_data, dry_run)
    batch_insert(cursor, insert_query_product_ids, subscription_product_ids_data, dry_run)

    if not dry_run:
        print("Finished inserting offering, subscription, subscription_measurement, and subscription_product_ids data.")


def delete_all_records(cursor, dry_run=False):
    delete_queries = [
        "DELETE FROM subscription_product_ids;",
        "DELETE FROM subscription_measurements;",
        "DELETE FROM subscription;",
        "DELETE FROM offering;"
    ]

    for query in delete_queries:
        if dry_run:
            print(query)
        else:
            cursor.execute(query)

    if not dry_run:
        print("All records have been deleted from the specified tables.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument('--dry-run', action='store_true',
                        help='Run the script in dry run mode to print SQL statements without executing')
    parser.add_argument('--force-delete-all', action='store_true',
                        help='Delete all existing data before inserting new data')
    args = parser.parse_args()

    main(args.dry_run, args.force_delete_all)
```


Features
--------

-   Generates random data for offerings, subscriptions, subscription measurements, and subscription product IDs.
-   Supports dry-run mode for previewing the SQL insert statements without executing them.
-   Allows for the deletion of all existing records in the specified tables before inserting new data.

Requirements
------------

-   Python 3.x
-   psycopg2 library
-   Access to a PostgreSQL database with the required schema

Installation
------------

1.  Ensure Python 3.x is installed on your system.
2.  Install `psycopg2` using pip:

    `pip install psycopg2-binary`

3.  Clone this repository or download the script directly to your working directory.

Usage
-----

The script can be run from the command line with various options to control its behavior.

### Basic Command

`python generate_subscription_data.py`

This command runs the script in its default mode, inserting generated data into the database.

### Dry-Run Mode

To preview the SQL insert statements without making any changes to the database, use the `--dry-run` option:

`python generate_subscription_data.py --dry-run`

### Delete All Existing Data

To delete all existing data from the relevant tables before inserting new data, use the `--force-delete-all` option:

`python generate_subscription_data.py --force-delete-all`

Warning: This will permanently remove all existing records in the specified tables.

### Combining Options

You can combine options as needed. For example, to preview the SQL statements for inserting data after deleting all existing records, run:

`python generate_subscription_data.py --dry-run --force-delete-all`