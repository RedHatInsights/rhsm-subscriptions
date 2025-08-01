import logging
import pytest
import datetime
import time
import uuid
from iqe_rhsm_subscriptions.utils.datetime_utils import get_hourly_range, get_future_month_range
from iqe_rhsm_subscriptions.rhsmlib.contract_service import contract_service_response

log = logging.getLogger(__name__)


@pytest.fixture(scope="session")
def application(swatch_user_app):
    return swatch_user_app


@pytest.mark.ephemeral
@pytest.mark.ephemeral_only
def test_verify_ansible_usage(application, kafka_consumer):
    """Verify ansible-aap-managed events, tally, usage, remittance.
    metadata:
        assignee: karshah
        negative: false
        importance: high
        level: high
        requirements: payg_tally
        test_steps:
            This is a component based test for swatch-billable-usage
            1. Create wiremock contract without metric in middle of month
            2. Publish tally snapshot to Kafka topic for current month to create gratis usage
            3. Publish tally snapshot to Kafka topic for next month to create "no" gratis usage
            4. Check remittance table to see both gratis and no gratis usage is created
            5. Create wiremock contract with metric in middle of month
            6. This shouldn't create any billable remittance usage since it will be covered.
        expected_results:
            1. Remittance should have gratis status and pending status
    """
    # Setup data
    # Create wiremock contract without metric in middle of month
    contract_start_date, contract_end_date = get_future_month_range(x=2)
    contract_mock_response = contract_service_response.get_json_data(
        subscription_number="sub123",
        sku="sku456",
        billing_provider="aws",
        billing_provider_id="1234;1234;1234",
        billing_account_id=str(uuid.uuid4()),
        product_tags=["ansible-aap-managed"],
        org_id=application.user.identity.org_id,
        start_date=contract_start_date,
        end_date=contract_end_date,
    )

    product_id = "ansible-aap-managed"
    billing_provider = "aws"
    billing_account_id = str(uuid.uuid4())
    gratis_snapshot_date = datetime.datetime.now(datetime.timezone.utc)
    metric_id = "MANAGED_NODES"
    value = 2.0

    # Creating the dictionary to pass as reqData to contract
    swatch_contract_req = {
        "org_id": application.user.identity.org_id,
        "product_tag": product_id,
        "billing_provider": billing_provider,
        "billing_account_id": billing_account_id,
    }
    application.rhsm_subscriptions.create_contract_wiremock(
        req_data=swatch_contract_req, resp_data=contract_mock_response
    )

    # Publish tally snapshot to Kafka topic for current month to create gratis usage
    gratis_tally_snapshot_uuid = application.rhsm_subscriptions.create_tally_snapshot(
        product_id=product_id,
        billing_provider=billing_provider,
        snapshot_date=gratis_snapshot_date,
        metric_id=metric_id,
        value=value,
        billing_account_id=billing_account_id,
    )

    assert gratis_tally_snapshot_uuid

    # Publish tally snapshot to Kafka topic for next month to create "no" gratis usage
    snapshot_date = datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=31)

    tally_snapshot_uuid = application.rhsm_subscriptions.create_tally_snapshot(
        product_id=product_id,
        billing_provider=billing_provider,
        snapshot_date=snapshot_date,
        metric_id=metric_id,
        value=value,
        billing_account_id=billing_account_id,
    )

    assert tally_snapshot_uuid

    time.sleep(5)

    # Check gratis remittance by tally_id
    gratis_remittance = application.rhsm_subscriptions.get_remittance_by_tally_id(
        gratis_tally_snapshot_uuid
    )
    assert gratis_remittance[0]["status"] == "GRATIS"

    # Check non gratis remittance by tally_id
    non_gratis_remittance = application.rhsm_subscriptions.get_remittance_by_tally_id(
        tally_snapshot_uuid
    )
    assert non_gratis_remittance[0]["status"] == "PENDING"

    # Check remittance table to see both gratis and no gratis usage is created
    beginning, ending = get_hourly_range(hours=1)
    remittance = application.rhsm_subscriptions.get_account_remittance(
        product_id=product_id,
        beginning=beginning,
        ending=ending,
        billingProvider="aws",
        billingAccountId=billing_account_id,
        metric_id=metric_id.replace("-", "_").lower(),
    )

    assert remittance
    statuses = [item["remittanceStatus"] for item in remittance]

    assert "pending" in statuses
    assert "gratis" in statuses

    # Create wiremock contract with metric in middle of month that is covered
    contract_with_metric_mock_response = contract_service_response.get_json_data(
        subscription_number="sub123",
        sku="sku456",
        billing_provider="aws",
        billing_provider_id="1234;1234;1234",
        billing_account_id="aws",
        product_tags=["ansible-aap-managed"],
        org_id=application.user.identity.org_id,
        start_date=contract_start_date,
        end_date=contract_end_date,
        metrics=[{"metric_id": "managed_node", "value": "2"}],
    )

    application.rhsm_subscriptions.create_contract_wiremock(
        req_data=swatch_contract_req, resp_data=contract_with_metric_mock_response
    )

    snapshot_date = datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=31)
    tally_snapshot_uuid = application.rhsm_subscriptions.create_tally_snapshot(
        product_id=product_id,
        billing_provider=billing_provider,
        snapshot_date=snapshot_date,
        metric_id=metric_id,
        value=value,
        billing_account_id=billing_account_id,
    )

    assert tally_snapshot_uuid

    # This shouldn't create any billable remittance usage since it will be covered.
    remittance = application.rhsm_subscriptions.get_account_remittance(
        product_id=product_id,
        beginning=beginning,
        ending=ending,
        billingProvider="aws",
        billingAccountId=billing_account_id,
        metric_id=metric_id.replace("-", "_").lower(),
    )

    assert len(remittance) == 2
