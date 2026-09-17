package workshop.harness;

import java.util.List;

/**
 * One attempt, and everything the loop learned about it.
 *
 * <p>The prompt is part of the evidence, not just an input: a repair that omitted a
 * failing finding is a different attempt from one that carried all of them.
 *
 * <p>Findings are bound to the attempt that produced them. That binding is the point:
 * a PASS belongs to one exact version of the source, and cannot be carried forward to
 * stand in for a result you never collected.
 */
public record AttemptReport(String label, String prompt, AgentResult agent,
                            List<Finding> findings) { }
