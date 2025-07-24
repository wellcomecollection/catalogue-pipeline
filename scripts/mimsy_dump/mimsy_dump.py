#!/usr/bin/env python3
"""
MIMSY Database Dump CLI Script

This script connects to a MIMSY Oracle database and dumps specified views to CSV files.
It can optionally create a ZIP file containing all the dumped files.
"""

import argparse
import boto3
import os
import sys
import zipfile
from datetime import datetime
from sqlalchemy import create_engine, text
import pandas as pd


def get_secret(secret_name, region_name="eu-west-1", profile_name="platform-developer"):
    """Retrieve a secret from AWS Secrets Manager."""
    try:
        session = boto3.session.Session(profile_name=profile_name)
        client = session.client(service_name="secretsmanager", region_name=region_name)

        get_secret_value_response = client.get_secret_value(SecretId=secret_name)
        return get_secret_value_response["SecretString"]
    except Exception as e:
        print(f"Error retrieving secret {secret_name}: {e}")
        return None


def get_engine_for_oracle(connection_string):
    """Create engine for an Oracle database."""
    return create_engine(connection_string)


def dump_query(connection, query, file_name, output_dir="."):
    """Dump query results to a CSV file."""
    try:
        result = connection.execute(text(query))
        result_df = result.mappings().all()
        df = pd.DataFrame(result_df)

        full_filename = os.path.join(output_dir, f"mimsy_{file_name.lower()}.csv")
        df.to_csv(full_filename, index=False)

        print(f"Dumped {len(df)} rows to {full_filename}")
        return full_filename
    except Exception as e:
        print(f"Error dumping query for {file_name}: {e}")
        return None


def view_dump_query(view_name):
    """Generate a query to dump all data from a view."""
    return f"select * from XGVIEWS.{view_name}"


def get_exhibition_items_join_query():
    """Get the complex exhibition items join query."""
    return """
WITH AggregatedNumbers AS (
    SELECT
        mkey,
        LISTAGG(other_number, ', ') WITHIN GROUP (ORDER BY other_number) AS other_numbers_list
    FROM
        XGVIEWS.VW_OTHER_NUMBERS
    GROUP BY
        mkey
)
SELECT DISTINCT
    VW_CATALOGUE.ID_NUMBER,
    VW_CATALOGUE.TITLE,
    VW_CATALOGUE.LEGAL_STATUS,
    VW_CATALOGUE.category1,
    VW_EXHIBITION_ITEMS.*,
    AggregatedNumbers.other_numbers_list
FROM
    XGVIEWS.VW_CATALOGUE
LEFT OUTER JOIN
    XGVIEWS.VW_EXHIBITION_ITEMS
ON
    VW_CATALOGUE.m_id = VW_EXHIBITION_ITEMS.m_id
LEFT OUTER JOIN
    AggregatedNumbers
ON
    VW_CATALOGUE.m_id = AggregatedNumbers.mkey
"""


def create_zip_file(files, output_dir="."):
    """Create a ZIP file containing all dumped files."""
    zip_filename = os.path.join(
        output_dir, f"mimsy_dump_{datetime.now().strftime('%Y%m%d_%H%M%S')}.zip"
    )

    try:
        with zipfile.ZipFile(zip_filename, "w") as zipf:
            for file in files:
                if file and os.path.exists(file):
                    zipf.write(file, arcname=os.path.basename(file))
                    print(f"Added {file} to {zip_filename}")

        print(f"ZIP file created: {zip_filename}")
        return zip_filename
    except Exception as e:
        print(f"Error creating ZIP file: {e}")
        return None


def main():
    parser = argparse.ArgumentParser(
        description="Dump MIMSY Oracle database views to CSV files"
    )
    parser.add_argument(
        "--secret-name",
        default="mimsy_dump/connection_string",
        help="AWS Secrets Manager secret name for database connection string",
    )
    parser.add_argument(
        "--profile", default="platform-developer", help="AWS profile name to use"
    )
    parser.add_argument(
        "--region", default="eu-west-1", help="AWS region for Secrets Manager"
    )
    parser.add_argument(
        "--output-dir", default=".", help="Output directory for CSV files and ZIP file"
    )
    parser.add_argument(
        "--no-zip", action="store_true", help="Skip creating a ZIP file"
    )
    parser.add_argument(
        "--views",
        nargs="*",
        default=[
            "VW_OTHER_NUMBERS",
            "VW_EXHIBITION_ITEMS",
            "VW_EXHIBITIONS",
            "VW_CATALOGUE",
            "VW_CONSERVATION",
            "VW_CONSERVATION_STATUS",
            "VW_CONDITION",
            "VW_LOAN_ITEMS",
        ],
        help="List of views to dump (default: all interesting views)",
    )
    parser.add_argument(
        "--include-join",
        action="store_true",
        default=True,
        help="Include the exhibition items join query",
    )

    args = parser.parse_args()

    # Create output directory if it doesn't exist
    os.makedirs(args.output_dir, exist_ok=True)

    # Get database connection string from AWS Secrets Manager
    print(f"Retrieving connection string from secret: {args.secret_name}")
    connection_string = get_secret(args.secret_name, args.region, args.profile)

    if not connection_string:
        print("Failed to retrieve database connection string")
        sys.exit(1)

    # Connect to database
    try:
        print("Connecting to Oracle database...")
        engine = get_engine_for_oracle(connection_string)
        connection = engine.connect()
        print("Connected successfully")
    except Exception as e:
        print(f"Error connecting to database: {e}")
        sys.exit(1)

    dumped_files = []

    # Dump specified views
    for view in args.views:
        print(f"Dumping view: {view}")
        query = view_dump_query(view)
        filename = dump_query(connection, query, view, args.output_dir)
        if filename:
            dumped_files.append(filename)

    # Dump exhibition items join if requested
    if args.include_join:
        print("Dumping exhibition items join query...")
        join_query = get_exhibition_items_join_query()
        filename = dump_query(
            connection, join_query, "exhibition_items_join", args.output_dir
        )
        if filename:
            dumped_files.append(filename)

    # Close database connection
    connection.close()

    # Create ZIP file unless --no-zip is specified
    if not args.no_zip:
        if dumped_files:
            zip_file = create_zip_file(dumped_files, args.output_dir)
            if zip_file:
                print(f"\nAll files successfully dumped and zipped to: {zip_file}")
        else:
            print("No files were dumped, skipping ZIP creation")
    else:
        print(f"\nAll files successfully dumped to: {args.output_dir}")
        for file in dumped_files:
            print(f"  - {file}")


if __name__ == "__main__":
    main()
