PANDOC=pandoc

REPORTS=$(patsubst %.md,%.pdf,$(wildcard *.md))

.PHONY: all clean

all: $(REPORTS)

%.pdf: %.md
	$(PANDOC) --citeproc $< -o $@

%_PRESENTATION.pdf: %_PRESENTATION.md
	$(PANDOC) --citeproc -t beamer $< -o $@

clean:
	rm -f $(REPORTS)
