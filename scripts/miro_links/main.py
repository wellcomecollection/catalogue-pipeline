#!/usr/bin/env python3

import re
import requests
import pandas as pd
from tqdm import tqdm
from concurrent.futures import ThreadPoolExecutor, as_completed

base_url = "https://api.wellcomecollection.org/catalogue/v2/works?partOf=qzcbm8q3&pageSize=100&include=notes"


def fetch_and_extract_all_pages(start_url, max_workers=4):
    """
    Fetch and extract all pages in parallel as they are discovered, updating tqdm manually.
    Submits new page fetches as nextPage URLs are found, and parallelises B number lookups per page.
    Returns a list of dicts with work and B number data for all pages.
    """
    all_workdata = []
    seen_pages = set()
    futures = []
    with ThreadPoolExecutor(max_workers=max_workers) as executor:
        # First request to get totalPages and first page
        r = requests.get(start_url)
        data = r.json()
        total_pages = data.get("totalPages", 1)
        pbar = tqdm(total=total_pages, desc="Processing pages")

        def submit_page(url):
            if url and url not in seen_pages:
                seen_pages.add(url)
                futures.append(executor.submit(fetch_and_extract_page, url))

        # Submit first page
        submit_page(start_url)

        # If nextPage exists, submit it
        next_url = data.get("nextPage")
        submit_page(next_url)

        # Process results as they complete, submitting new pages as we go
        while futures:
            done, futures = wait_first(futures)
            for fut in done:
                page_data, next_page_url = fut.result()
                all_workdata.extend(page_data)
                pbar.update(1)
                submit_page(next_page_url)
        pbar.close()

    return all_workdata


def fetch_and_extract_page(url):
    """
    Fetch a single page of works, extract related work IDs, and fetch B numbers for each related work in parallel.
    Returns a tuple: (list of work dicts, nextPage URL).
    """
    r = requests.get(url)
    data = r.json()
    workdata = []
    results = data.get("results", [])
    bnumber_context = []  # (work, related_work)
    for work in results:
        if work["type"] == "Work":
            for note in work["notes"]:
                if note["noteType"]["id"] == "related-material":
                    related_works = process_note(note["contents"])
                    if len(related_works) > 0:
                        for related_work in related_works:
                            bnumber_context.append((work, related_work))
                    break
    # Parallelise B number lookups for this page
    with ThreadPoolExecutor(max_workers=8) as bnumber_executor:
        future_to_ctx = {
            bnumber_executor.submit(get_bnumbers, related_work): (work, related_work)
            for work, related_work in bnumber_context
        }
        for future in as_completed(future_to_ctx):
            work, related_work = future_to_ctx[future]
            try:
                bnumbers = future.result()
                for bnumber in bnumbers:
                    workdata.append(
                        {
                            "Calm Work ID": work["id"],
                            "Calm AltRefNo": work["referenceNumber"],
                            "Sierra Work ID": related_work,
                            "Sierra B Number": bnumber,
                        }
                    )
            except Exception as e:
                print(
                    f"Error fetching B numbers for {work['id']} and {related_work}: {e}"
                )
                pass

    next_page_url = data.get("nextPage")
    return workdata, next_page_url


def wait_first(futures):
    """
    Wait for the first future(s) to complete, return (done, not_done).
    Used to process page fetches as they finish.
    """
    import concurrent.futures

    done, not_done = concurrent.futures.wait(
        futures, return_when=concurrent.futures.FIRST_COMPLETED
    )
    return done, list(not_done)


def process_note(contents):
    """
    Extract related work IDs from note contents using regex.
    Returns a list of related work IDs.
    """
    related_works = []
    for entry in contents:
        matches = re.findall(
            r'https://wellcomecollection.org/works/([^\'"]+)[\'"]', entry
        )
        for match in matches:
            related_works.append(match)
    return related_works


def get_bnumbers(id):
    """
    Fetch the B numbers (Sierra system numbers) for a given work ID.
    Returns a list of B numbers (strings).
    """
    bnumbers = []
    r = requests.get(
        f"https://api.wellcomecollection.org/catalogue/v2/works/{id}?include=identifiers"
    )
    data = r.json()
    if "identifiers" in data:
        for identifier in data["identifiers"]:
            if identifier["identifierType"]["id"] == "sierra-system-number":
                bnumbers.append(identifier["value"])
    return bnumbers


def main():
    """
    Main entry point: fetch all work and B number data, write to CSV.
    """
    print("Fetching and processing pages in parallel...")
    all_workdata = fetch_and_extract_all_pages(base_url, max_workers=4)
    df = pd.DataFrame(all_workdata)
    print(f"Writing {len(df)} rows to mirolinks.csv...")
    df.to_csv("mirolinks.csv", index=False)


if __name__ == "__main__":
    main()
