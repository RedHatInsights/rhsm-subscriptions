def test_plugin_accessible(app):
    app.poc
    assert True


def test_list_endpoints(app):
    assert '/api/ingress' in app.poc.get_endpoints()
