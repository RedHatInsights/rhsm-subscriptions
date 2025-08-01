import attr
import importscan
from iqe.base.application.plugins import ApplicationPlugin, ApplicationPluginException

from . import fixtures


class ApplicationpocException(ApplicationPluginException):
    """Basic Exception for poc object"""

    pass


@attr.s
class Applicationpoc(ApplicationPlugin):
    """Holder for application poc related methods and functions"""

    plugin_app_name = "poc"
    plugin_real_name = "poc"
    plugin_name = "poc"
    plugin_title = "poc"
    plugin_package_name = "iqe-poc-plugin"

    def get_endpoints(self):
        resp = self.application.http_client.get(self.application.api_address)
        return resp.json()['services']


importscan.scan(fixtures)
