# Cache miss analysis for loop nests 

This program estimates the cache misses for a program with perfect loop nests and simple indexing.
:w
It takes as input the cache size, block size, the number of sets, associativity and a 1D,2D or 3D array along with a perfect loop nest and outputs the number of cache misses for each input array.

## Build and Run

`./run.sh <testcase file>`

For example,

`./run.sh ./testcases1D.java`
