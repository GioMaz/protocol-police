Course Project
The Project

The course will conclude with a group project, to be carried out over the span of approximately 4 weeks. The primary goal is to implement and experiment with a static analysis concept or idea, using our course material or existing research literature as source material. Our target language is MiniScala.

This project is deliberately open-ended. You are expected to implement as many features as possible, but not necessarily everything in your source material. Start with supporting the MiniScala features that are present in the source material. E.g., if the source material phrase the analysis on language that lacks higher-order features, start by ignoring those in MiniScala as well. 

It is also expected that you may have to deviate from the literature to handle MiniScala features that are not present in those sources. Because of this open-endedness, you are strongly encouraged to proceed incrementally by starting with a core set of supported features and expanding your implementation from there. The project grade counts for 50% of your total homework assignments (and, thus, 10% of the total grade).
Use of AI

You can freely use AI for the entirety of the project, and you are highly encouraged to do so (the projects are deliberately ambitious because of AI assistance). This includes generating code and tests, but also assisting your understanding of the papers and sources on which your project is based, and helping you decide on open-ended design choices.
Project Suggestions

You are free to choose any project you like, as long as it aligns with the topic of the course. If you are in doubt, please email Andreas. Below are a few suggestions:

    Implement a taint analysis, e.g., as described in Boomerang: Demand-Driven Flow- and Context-Sensitive Pointer Analysis for Java, or TAJ: Effective Taint Analysis of Web Applications.

    Implement and experiment with heuristics for efficient pointer analysis, e.g., including those in Online Cycle Detection and Difference Propagation: Applications to Pointer Analysis and Wave Propagation and Deep Propagation for Pointer Analysis.

    Implement the IFDS and IDE frameworks, as explained in Section 9 of SPA. You may also read the original papers (IFDS, IDE), as well as this implementation paper for IFDS.

    Implement various approaches to context-sensitivity for pointer analysis, e.g., as described in Pick Your Contexts Well: Understanding Object-Sensitivity and Hybrid Context-Sensitivity for Points-To Analysis.

    Implement Hindley-Milner type inference for the functional subset of MiniScala, by adapting Algorithm W.

    Implement an interprocedural slicer for MiniScala, e.g., by following the course material.

    Formalize a static analysis (e.g., interval analysis or sign analysis) in a proof assistant, e.g., by following Certified Static Analysis by Abstract Interpretation.

Deliverables
Project Proposal

The project proposal is due on November 5. Aim for 2–3 pages of text explaining your project idea, the motivation behind it, and how you plan to break down the process. You should outline your milestones and expected timeline, remembering to allocate a few days at the end for the report and final presentation. If you need to handle language features not present in the source material, briefly explain the approach you plan to take. Additionally, describe how you anticipate interacting with AI to carry out the project efficiently, and outline your testing strategy to gain trust that your implementation works correctly (hint: you can use AI to write unit and end-to-end tests).

There will be proposal presentations on Monday, November 9. Plan for a 3–4 minute presentation (3–5 slides) overviewing your proposal, and ensure you send all slides prior to presenting.
Project Report

The project report is due on December 4. The report should be around 8–10 pages of text and must include several key sections. Begin with a citation-heavy introduction (1–2 pages) explaining the problem setting, the motivation, and what you deliver at a high level. Follow this with a technical section (2–3 pages) that rigorously explains the setting and analysis or problem formulation; be sure to detail how you handled any language features not covered by your source material.

You also need to provide a description of your project structure, referencing the code files where various functionalities have been implemented. Dedicate 1–2 pages to your evaluation and experimentation, describing your testing, your dataset, and instructions on how to execute your project on custom inputs. Finally, include a 1–2 page reflection on your interaction with AI. Discuss which models you used, how you integrated them, the tasks you delegated, and how you checked AI-generated code for validity. Reflect on how often the generated code missed the mark and analyze why this might have happened (e.g., ambiguous prompts or tasks that were too large).
Final Presentations

The final presentations will take place on Thursday, December 3. Each group will present for 3–4 minutes (3–5 slides), overviewing your project, highlighting any fun features you implemented, challenges you faced, and your overall experience. Please send all slides to Andreas by 10:00 AM on December 3.
