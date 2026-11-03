PANDOC=pandoc

REPORTS=$(patsubst %.md,%.pdf,$(wildcard *.md))

.PHONY: all clean

all: $(REPORTS)

%.pdf: %.md
	$(PANDOC) $< -o $@

clean:
	rm -f *.pdf
