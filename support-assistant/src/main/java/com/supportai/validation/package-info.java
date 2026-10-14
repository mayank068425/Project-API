/**
 * Verification of model output.
 *
 * <h2>Why these live in {@code validation} and not somewhere AI-shaped</h2>
 * A validator checks that something is fit to be used before it is used. That is
 * exactly what these classes do — {@code CitationVerifier} checks an answer
 * against the sources it was shown, {@code RcaVerifier} checks findings against
 * the evidence, {@code Redactor} checks a customer draft for material that must
 * not leave the building.
 *
 * <p>Putting them here rather than in an {@code ai} package is a claim about how
 * they should be treated: model output is untrusted input, and it goes through
 * validation like any other untrusted input. A separate AI package invites the
 * idea that these are optional AI plumbing rather than the checks that make the
 * feature safe to ship.
 *
 * <h2>The rule they all implement</h2>
 * Anything that cannot be verified is <em>withheld, not caveated</em>. A warning
 * above an answer containing a subtly wrong command does not stop anyone running
 * it — during an incident, least of all.
 */
package com.supportai.validation;
