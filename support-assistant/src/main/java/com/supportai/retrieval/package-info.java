/**
 * Document chunking and hybrid retrieval.
 *
 * <h2>Why this package exists alongside the layered structure</h2>
 * The rest of this application follows a conventional layering — controller,
 * service, serviceimpl, repository. Retrieval does not fit any of those. It is
 * neither a service in the orchestration sense nor a repository in the
 * persistence sense: it is an algorithmic layer with its own contract, whose
 * inputs are a query and a clearance and whose output is a ranked list with the
 * scoring shown.
 *
 * <p>Folding it into {@code serviceimpl} would bury the two decisions that matter
 * most in this system — how documents are split, and how three rankers are
 * combined — inside classes named after use cases. Both are worth finding.
 *
 * <p>This is the same reasoning that put {@code jdbc/} beside the services in the
 * library system: a distinct kind of problem gets a package rather than being
 * dissolved into the nearest layer that will take it.
 */
package com.supportai.retrieval;
