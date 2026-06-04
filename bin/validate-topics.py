#!/usr/bin/env python3
import os
import re
import sys

try:
    import yaml
except ImportError:
    print("python3-pyyaml missing. dnf install python3-pyyaml or pip install PyYAML")
    sys.exit(1)


# object used to track references to topics
class TopicReference:
    def __init__(self, topic):
        self.topic = topic
        self.clowder_lookup = None
        self.clowdapp_template = None
        self.properties_path = None
        self.clowdapp_resource_path = None

    def set_clowdapp_template(self, clowdapp_template):
        self.clowdapp_template = clowdapp_template

    def set_clowdapp_resource_path(self, clowdapp_resource_path):
        self.clowdapp_resource_path = clowdapp_resource_path

    def set_clowder_lookup(self, value):
        self.clowder_lookup = value

    def set_properties_path(self, properties_path):
        self.properties_path = properties_path

    def __repr__(self):
        return f"{self.topic} clowdapp={self.clowdapp_template} properties={self.properties_path}"

    def __eq__(self, other):
        return str(self) == str(other)

    def __hash__(self):
        return hash(str(self))


def find_clowdapp_templates():
    templates = []
    for dirpath, _, filenames in os.walk("."):
        for filename in filenames:
            path = os.path.join(dirpath, filename)
            if path.endswith("clowdapp.yaml"):
                templates.append(path)
    return templates


def find_clowdapps():
    clowdapps = []
    for template_path in find_clowdapp_templates():
        template = yaml.safe_load(open(template_path).read())
        template_clowdapps = [
            obj for obj in template["objects"] if obj["kind"] == "ClowdApp"
        ]
        for clowdapp in template_clowdapps:
            clowdapp["_template"] = template_path
            clowdapp["_profiles"] = extract_clowdapp_profiles(clowdapp)
            clowdapp["_topics"] = extract_clowdapp_topics(clowdapp)
        clowdapps.extend(template_clowdapps)
    return clowdapps


def extract_clowdapp_profiles(clowdapp):
    profiles = set()
    for resource in clowdapp["spec"].get("deployments", []) + clowdapp["spec"].get(
        "jobs", []
    ):
        for env in resource["podSpec"]["env"]:
            if env["name"] == "SPRING_PROFILES_ACTIVE":
                profiles.update(env["value"].split(","))
    return sorted(profiles)


def extract_clowdapp_topics(clowdapp):
    topics = set()
    for topic_definition in clowdapp["spec"].get("kafkaTopics", []):
        topics.add(topic_definition["topicName"])
    return topics


def find_springboot_yaml_files():
    yamls = []
    for dirpath, _, filenames in os.walk("."):
        for filename in filenames:
            path = os.path.join(dirpath, filename)
            if (
                "src/main" in path
                and path.endswith(".yaml")
                and "application" in path
                and "swatch-core-test" not in path
            ):
                yamls.append(path)
    return yamls


def find_clowdapp_topic_references(clowdapps):
    references = []
    for clowdapp in clowdapps:
        for topic in clowdapp["_topics"]:
            reference = TopicReference(topic)
            reference.set_clowdapp_template(clowdapp["_template"])
            references.append(reference)
    return references


def find_springboot_topic_references(springboot_yaml_files):
    topics = []
    for path in find_springboot_yaml_files():
        token_iter = iter(yaml.scan(open(path).read()))
        for token in token_iter:
            if type(token) == yaml.ScalarToken:
                if "platform." in token.value:
                    matches = re.findall("platform.[a-zA-Z.-]+", token.value)
                    for match in set(matches):
                        reference = TopicReference(match)
                        reference.set_properties_path(path)
                        reference.set_clowder_lookup(
                            f"requestedName == '{match}'" in token.value
                        )
                        topics.append(reference)
    return topics


def find_quarkus_properties_files():
    paths = []
    for dirpath, _, filenames in os.walk("."):
        for filename in filenames:
            path = os.path.join(dirpath, filename)
            if (
                "src/main" in path
                and os.path.basename(path) == "application.properties"
            ):
                paths.append(path)
    return paths


def find_quarkus_topic_references(paths):
    references = []
    for path in paths:
        topics = set()
        for line in open(path).readlines():
            if line.startswith("mp.messaging.") and ".topic=" in line:
                topic = line.split("=")[-1].strip()
                topics.add(topic)
        for topic in topics:
            reference = TopicReference(topic)
            reference.set_properties_path(path)
            reference.set_clowder_lookup(
                True
            )  # clowder-config-source takes care of this :-)
            references.append(reference)
    return references


def find_clowdapp_path(path, clowdapps):
    # first we try the top-level directory
    top_dir = os.path.relpath(path).split(os.path.sep)[0]
    if os.path.exists(os.path.join(top_dir, "deploy", "clowdapp.yaml")):
        return os.path.join(".", os.path.join(top_dir, "deploy", "clowdapp.yaml"))
    # next we try inferring from path application-{profile}.yaml
    match = re.match("application-(.*).yaml", os.path.basename(path))
    if match:
        for clowdapp in clowdapps:
            if match.group(1) in clowdapp["_profiles"]:
                return clowdapp["_template"]


def validate(clowdapp_references, springboot_references, quarkus_references, clowdapps):
    print("🔍 Verifying that all quarkus topic references are in proper clowdapp")
    seen_clowdapp_references = set()
    failed = False
    for reference in quarkus_references:
        clowdapp_path = find_clowdapp_path(reference.properties_path, clowdapps)
        for clowdapp_reference in clowdapp_references:
            if (
                clowdapp_reference.clowdapp_template == clowdapp_path
                and clowdapp_reference.topic == reference.topic
            ):
                print(
                    f"✅ Quarkus properties file {reference.properties_path} references {reference.topic}, present in {clowdapp_path}."
                )
                seen_clowdapp_references.add(clowdapp_reference)
                break
        else:
            print(
                f"❌ ERROR Quarkus properties file {reference.properties_path} references {reference.topic}, not found in clowdapp template {clowdapp_path}."
            )
            failed = True

    print()
    print("🔍 Verifying that all SpringBoot topic references are in proper clowdapp")
    for reference in springboot_references:
        clowdapp_path = find_clowdapp_path(reference.properties_path, clowdapps)
        if clowdapp_path is None:
            print(
                f"❌ ERROR SpringBoot properties file {reference.properties_path} which is not associated to a specific ClowdApp template, references {reference.topic}."
            )
            failed = True
            continue
        for clowdapp_reference in clowdapp_references:
            if (
                clowdapp_reference.clowdapp_template == clowdapp_path
                and clowdapp_reference.topic == reference.topic
            ):
                print(
                    f"✅ SpringBoot properties file {reference.properties_path} references {reference.topic}, present in {clowdapp_path}."
                )
                seen_clowdapp_references.add(clowdapp_reference)
                break
        else:
            print(
                f"❌ ERROR SpringBoot properties file {reference.properties_path} references {reference.topic}, not found in clowdapp template {clowdapp_path}."
            )
            failed = True

    print()
    print(
        "🔍 Verifying that all SpringBoot topic references lookup actual topic name through clowder config"
    )
    for reference in springboot_references:
        if reference.clowder_lookup:
            print(
                f"✅ SpringBoot properties file {reference.properties_path} references {reference.topic}, uses clowder to configure actual topic name."
            )
        else:
            print(
                f"❌ ERROR SpringBoot properties file {reference.properties_path} references {reference.topic}, but does not use clowder to configure actual topic name."
            )
            failed = True

    print()
    print("🔍 Verifying that no unneeded topics referenced in clowdapps")
    for reference in clowdapp_references:
        if not reference in seen_clowdapp_references:
            print(
                f"❌ ERROR ClowdApp {reference.clowdapp_template} references {reference.topic} which didn't have a corresponding service-specific app property."
            )
            failed = True
    if len(seen_clowdapp_references) == len(clowdapp_references):
        print(f"✅ All ClowdApp topic references accounted for.")

    print()
    print("🔍 Verifying that no src/main/java files have topic references")
    java_file_has_topic_reference = False
    for dirname, _, filenames in os.walk("."):
        for filename in filenames:
            path = os.path.join(dirname, filename)
            if path.endswith(".java") and "src/main/java" in path:
                for index, line in enumerate(open(path).readlines()):
                    if "platform." in line:
                        match = re.search("platform.[a-zA-Z.-]+", line)
                        print(
                            f"❌ ERROR Java file {path}, line {index + 1} references {match.group(0)}."
                        )
                        java_file_has_topic_reference = True
                        failed = True
    if not java_file_has_topic_reference:
        print(f"✅ No src/main/java files have topic references.")

    if failed:
        print(f"❌ topic validation failed. See previous errors.")
        sys.exit(1)


clowdapps = find_clowdapps()
clowdapp_references = find_clowdapp_topic_references(clowdapps)
springboot_yaml_files = find_springboot_yaml_files()
springboot_references = find_springboot_topic_references(springboot_yaml_files)
quarkus_properties_files = find_quarkus_properties_files()
quarkus_references = find_quarkus_topic_references(quarkus_properties_files)
validate(
    clowdapp_references, springboot_references, quarkus_references, clowdapps=clowdapps
)
