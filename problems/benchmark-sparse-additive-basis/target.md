# Sparse additive bases for density-zero sets

For $X\subseteq\mathbb N_0$, write
$X(N)=|X\cap\{0,1,\ldots,N\}|$, and
$B+B=\{b_1+b_2:b_1,b_2\in B\}$. Say that $A$ has natural density zero
when $A(N)/(N+1)\to0$.

This frozen variant deliberately uses nonnegative integers throughout and
counts zero in both counting functions. Do not silently switch to a
positive-integer convention, which can introduce irrelevant boundary
counterexamples.

## Exact target

Is it true that for every density-zero set $A\subseteq\mathbb N_0$ there is
a set $B\subseteq\mathbb N_0$ such that $A\subseteq B+B$ and

\[
  B(N)=o(\sqrt N)?
\]

Resolve the universal statement. A negative answer requires one completely
defined density-zero $A$ and a proof that every $B$ with $A\subseteq B+B$
violates the little-o bound. A heuristic, one finite block, or an average-case
argument does not close the endpoint.
