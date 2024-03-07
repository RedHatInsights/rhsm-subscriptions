test:
	pytest --cov-report term-missing --cov=swatchdog tests/ -vv
unit-test:
	pytest --cov-report term-missing --cov=swatchdog tests/ -vv -m "not integration_test"
typecheck:
	mypy swatchdog
format:
	black swatchdog tests
format-check:
	black --check swatchdog tests

