# Setup for the IQE to Ephemeral Proxy

### This allows a user to run and debug the IQE python tests locally against a deployment on OpenShift
#### Python version 3.12 is currently required for this

1. Create an iqe-workspace directory  
`mkdir /home/[username]/iqe-workspace`
2. Copy get-iqe.py to the iqe-workspace directory:  
https://insights-qe.pages.redhat.com/iqe-core-docs/tutorial/part3.html#download-get-iqe-py
3. Run the script  
`python3.12 ./get-iqe.py`
4. Create a file in the iqe-workspace directory named iqe_env:  
`export ENV_FOR_DYNACONF=clowder_smoke`  
`export DYNACONF_IQE_VAULT_OIDC_HEADLESS=true`  
`export DYNACONF_SETTINGS__pre_check=0`    
`export NAMESPACE=[current namespace]`    
5. Activate the venv while in the iqe-workspace directory:  
`source activate_iqe_venv && source ./iqe_env`  
6. Clone the iqe-core and iqe-rhsm-subscriptions-plugin repos:  
`git clone git@gitlab.cee.redhat.com:insights-qe/iqe-rhsm-subscriptions-plugin.git`  
`git clone git@gitlab.cee.redhat.com:insights-qe/iqe-core.git`  
7. Pip install source directories for iqe-core and iqe-rhsm-subscriptions-plugin while in the venv:  
`pip install -e [/iqe-core location]`  
`pip install -e [/iqe-rhsm-subscriptions-plugin location]`
8. Create (or use existing) namespace and deploy swatch backend from bonfire:  
`oc login …`
`bonfire namespace reserve`
`bonfire deploy rhsm …`
9. Start the proxy:  
`../rhsm-subscriptions/bin/iqe-ee-proxy.sh`
10. Run iqe tests:  
`iqe tests plugin rhsm_subscriptions -m ephemeral [ -k one_test_i_want]`

**Note**: The CI runs ephemeral labeled tests only. You can run without the -m, but then it will run tests for other environments, like post_stage_deploy, which will most likely fail.

### For PyCharm use:  
Use JetBrains toolbox to install PyCharm since the flatpak version has issues.

Run -> Edit Configurations -> Edit Configuration templates ... -> Python tests -> pytest  
- Paths to ".env" files: /home/[username]/iqe-workspace/iqe_env 
- both iqe-core and iqe-rhsm-subscriptions-plugin tabs should have this value
- Additional arguments: -m ephemeral
- Ensure that the python interpreter is using ~/iqe-workspace/.iqe_env/bin/python3



