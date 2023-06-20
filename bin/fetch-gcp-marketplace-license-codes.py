#!/usr/bin/env python
# NOTE: this script requires the python google-cloud-compute library and
# to auth GCP API interactions via https://cloud.google.com/docs/authentication/provide-credentials-adc#local-dev
try:
    import google.cloud.compute_v1 as compute_v1
except ImportError:
    print("Failed to import GCP client, please pip install --user google-cloud-compute")
    sys.exit(1)

images_client = compute_v1.ImagesClient()
license_codes = {}
for project in ['rhel-cloud', 'rhel-sap-cloud']:
    request = compute_v1.ListImagesRequest(
        project=project, max_results=100,
        # NOTE: below argument can be used to limit output if needed in the future
        # filter="deprecated.state != DEPRECATED"
    )
    # NOTE: list method paginates where page size is the `max_results` value from above
    for image in images_client.list(request=request):
        family = image.family
        if family.startswith('rhel') and len(image.license_codes) > 0:
            license = image.license_codes[0]
            if license not in license_codes:
                license_codes[license] = set()
            license_codes[license].add(family)

for license, families in license_codes.items():
    print(f'"{license}", // {", ".join(families)}')
