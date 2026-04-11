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
