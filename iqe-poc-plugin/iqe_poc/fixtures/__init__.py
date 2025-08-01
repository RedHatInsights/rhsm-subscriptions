import pytest


@pytest.fixture(scope="session")
def poc_user_app(application):
    """Return application using POC plugin user configuration.
    
    This fixture provides an application instance configured for POC plugin testing.
    It follows the same pattern as other IQE plugins for user management.
    """
    # For now, use the default application since POC plugin doesn't have specific user config
    # In the future, this could be enhanced to use specific POC user configuration
    return application
