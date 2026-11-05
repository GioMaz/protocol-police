PANDOC=pandoc

REPORTS=$(patsubst %.md,%.pdf,$(wildcard *.md))

.PHONY: all clean

all: $(REPORTS)

%.pdf: %.md
	$(PANDOC) --citeproc $< -o $@

clean:
	rm -f $(REPORTS)
