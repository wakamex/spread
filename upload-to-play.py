#!/usr/bin/env python3
"""Upload AAB to Google Play closed alpha track."""

import argparse
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

PACKAGE_NAME = "app.spread"
AAB_PATH = "app/build/outputs/bundle/release/app-release.aab"
SCOPES = ["https://www.googleapis.com/auth/androidpublisher"]


def main():
    parser = argparse.ArgumentParser(description="Upload AAB to Google Play")
    parser.add_argument(
        "--credentials",
        default="play-service-account.json",
        help="Path to service account JSON key",
    )
    parser.add_argument(
        "--track",
        default="alpha",
        help="Release track (alpha, beta, production)",
    )
    parser.add_argument(
        "--aab",
        default=AAB_PATH,
        help="Path to AAB file",
    )
    parser.add_argument(
        "--notes",
        default="",
        help="Release notes",
    )
    args = parser.parse_args()

    credentials = service_account.Credentials.from_service_account_file(
        args.credentials, scopes=SCOPES
    )
    service = build("androidpublisher", "v3", credentials=credentials)

    # Create a new edit
    edit = service.edits().insert(body={}, packageName=PACKAGE_NAME).execute()
    edit_id = edit["id"]
    print(f"Created edit: {edit_id}")

    # Upload the AAB
    print(f"Uploading {args.aab}...")
    media = MediaFileUpload(args.aab, mimetype="application/octet-stream")
    bundle = (
        service.edits()
        .bundles()
        .upload(packageName=PACKAGE_NAME, editId=edit_id, media_body=media)
        .execute()
    )
    version_code = bundle["versionCode"]
    print(f"Uploaded bundle: versionCode={version_code}")

    # Assign to track
    track_body = {
        "releases": [
            {
                "versionCodes": [str(version_code)],
                "status": "completed",
                "releaseNotes": [{"language": "en-US", "text": args.notes}]
                if args.notes
                else [],
            }
        ]
    }
    service.edits().tracks().update(
        packageName=PACKAGE_NAME,
        editId=edit_id,
        track=args.track,
        body=track_body,
    ).execute()
    print(f"Assigned to track: {args.track}")

    # Commit the edit
    service.edits().commit(packageName=PACKAGE_NAME, editId=edit_id).execute()
    print("Committed! Release is live.")


if __name__ == "__main__":
    main()
