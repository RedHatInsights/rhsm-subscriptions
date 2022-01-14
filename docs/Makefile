.PHONY:
all: $(patsubst %.puml,%.svg,$(wildcard *.puml))

%.svg: %.puml
	test -n "$$(podman images -q -f reference=localhost/plantuml)" || podman build -f Dockerfile.plantuml . -t plantuml
	podman run --rm -v $$(pwd):/mnt:z plantuml plantuml -tsvg $<
