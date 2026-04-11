# References

Background reading for the metrics and ideas behind `gordian`.

## Foundational coupling metrics

- Stevens, W. P., Myers, G. J., & Constantine, L. L. (1974). *Structured
  Design*. IBM Systems Journal, 13(2), 115–139. The original treatment of
  module coupling and cohesion.

- Chidamber, S. R., & Kemerer, C. F. (1994). *A Metrics Suite for Object
  Oriented Design*. IEEE Transactions on Software Engineering, 20(6), 476–493.
  Introduced CBO (Coupling Between Objects) and RFC (Response For a Class).

- Martin, R. C. (1994). *OO Design Quality Metrics: An Analysis of
  Dependencies*. Defines afferent coupling (Ca), efferent coupling (Ce),
  instability (I), and abstractness (A). The vocabulary `gordian` uses most
  directly.

## Dependency graphs and architectural complexity

- MacCormack, A., Rusnak, J., & Baldwin, C. Y. (2006). *Exploring the Structure
  of Complex Software Designs: An Empirical Study of Open Source and
  Proprietary Code*. Management Science, 52(7), 1015–1030. Introduces
  **propagation cost** — the fraction of the system reachable through the
  transitive dependency graph. The core metric `gordian` reports.

- Baldwin, C. Y., & Clark, K. B. (2000). *Design Rules, Vol. 1: The Power of
  Modularity*. MIT Press. The theoretical underpinning for Design Structure
  Matrices (DSMs) and modularity as an economic property of systems.

- MacCormack, A., Baldwin, C. Y., & Rusnak, J. (2012). *Hidden Structure: Using
  Network Theory to Detect the Evolution of Software Architecture*. Research
  Policy, 41(8), 1309–1324. Core/periphery classification of components in
  dependency graphs.

- Melton, H., & Tempero, E. (2007). *An Empirical Study of Cycles Among Classes
  in Java*. Empirical Software Engineering, 12(4), 389–415. Strongly connected
  components in import graphs as a coupling signal.

## Software as a complex network

- Valverde, S., & Solé, R. V. (2003). *Hierarchical Small Worlds in Software
  Architecture*. Dynamics of Continuous, Discrete and Impulsive Systems.
  Applies complex-network measures to software dependency graphs.

- LaBelle, N., & Wallingford, E. (2004). *Inter-Package Dependency Networks in
  Open-Source Software*. arXiv:cs/0411096. Package-level dependency network
  analysis of Debian and similar ecosystems.

## Ecosystem-scale dependency studies

- Decan, A., Mens, T., & Grosjean, P. (2019). *An Empirical Comparison of
  Dependency Network Evolution in Seven Software Packaging Ecosystems*.
  Empirical Software Engineering, 24(1), 381–416. npm, PyPI, CRAN, RubyGems,
  and others — how coupling grows and how fragile these networks become.

## Tools in the same lineage

- **Structure101**, **NDepend**, **Lattix**, **Sonargraph** — commercial tools
  built around DSMs and dependency metrics.
- **madge** — JavaScript dependency graph visualizer.
- **clj-kondo** — not a coupling tool, but its analysis output is a convenient
  source of Clojure namespace dependency data.

## Further reading

Search terms that surface the rest of the literature: *propagation cost
software*, *design structure matrix software architecture*, *architectural
complexity MacCormack*, *software coupling metrics empirical*, *software as a
complex network*.


## Change coupling (logical / evolutionary coupling)

- Gall, H., Hajek, K., & Jazayeri, M. (1998). *Detection of Logical Coupling
  Based on Product Release History*. Proceedings of ICSM 1998, 190–198.

- Gall, H., Jazayeri, M., & Krajewski, J. (2003). *CVS Release History Data for
  Detecting Logical Coupling*. IWPSE 2003.

- Zimmermann, T., Weißgerber, P., Diehl, S., & Zeller, A. (2005). *Mining
  Version Histories to Guide Software Changes*. IEEE Transactions on Software
  Engineering, 31(6), 429–445.

- D'Ambros, M., Lanza, M., & Robbes, R. (2009). *On the Relationship Between
  Change Coupling and Software Defects*. WCRE 2009.

- Oliva, G. A., & Gerosa, M. A. (2015). *Experience Report: How Do Structural
  Dependencies Influence Change Propagation? An Empirical Study*. ISSRE 2015.

## Architecture conformance and layering violations

- Murphy, G. C., Notkin, D., & Sullivan, K. (1995). *Software Reflexion Models:
  Bridging the Gap Between Source and High-Level Models*. FSE 1995. Extended
  in IEEE Transactions on Software Engineering (2001), 27(4), 364–380.

- Koschke, R., & Simon, D. (2003). *Hierarchical Reflexion Models*. WCRE 2003.

- Passos, L., Terra, R., Valente, M. T., Diniz, R., & Mendonca, N. (2010).
  *Static Architecture-Conformance Checking: An Illustrative Overview*. IEEE
  Software, 27(5), 82–89.

- Herold, S., & Rausch, A. (2013). *Complementing Model-Driven Development for
  the Detection of Software Architecture Erosion*. MiSE 2013.

### Tooling prior art

- **ArchUnit** — architecture conformance testing for Java.
- **dependency-cruiser** — dependency rule enforcement for JavaScript.

## Conceptual coupling (TF-IDF cosine similarity on namespace vocabulary)

gordian's conceptual coupling analysis extracts terms from namespace names,
function names, and docstrings; builds TF-IDF vectors per namespace; and
ranks pairs by cosine similarity.  Terms are normalised by a targeted English
suffix stemmer (Porter-style rules for -ing, -ed, -er, -able, -ness, -ly,
-es, -s) and a stop-word list.

### Conceptual coupling metrics

- Poshyvanyk, D., & Marcus, A. (2006). *The Conceptual Coupling Metrics for
  Object-Oriented Systems*. ICSM 2006, 469–478.  Original definition of
  conceptual coupling between classes using IR techniques.

- Poshyvanyk, D., Gueheneuc, Y.-G., Marcus, A., Antoniol, G., & Rajlich, V.
  (2007). *Feature Location Using Probabilistic Ranking of Methods Based on
  Execution Scenarios and Information Retrieval*. IEEE Transactions on
  Software Engineering, 33(6), 420–432.

- Bavota, G., Dit, B., Oliveto, R., Di Penta, M., Poshyvanyk, D., & De Lucia,
  A. (2013). *An Empirical Study on the Developers' Perception of Software
  Coupling*. ICSE 2013.

### Information retrieval foundations

- Salton, G., & Buckley, C. (1988). *Term-Weighting Approaches in Automatic
  Text Retrieval*. Information Processing & Management, 24(5), 513–523.
  Foundational paper for TF-IDF weighting.

- Porter, M. F. (1980). *An Algorithm for Suffix Stripping*. Program, 14(3),
  130–137.  The Porter stemmer algorithm; gordian implements a targeted
  subset of the suffix rules.

### Informal / rationale sources

- Hickey, R. *The Language of the System*. Conference talk.
- Hickey, R. *Maybe Not*. Clojure/conj talk.
