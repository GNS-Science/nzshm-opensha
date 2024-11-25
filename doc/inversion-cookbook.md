# Inversion Cookbook

These are the parameters to control inversion execution:

- repeatable
- iterationCompletionCriteria
- energyChangeCompletionCriteria
- selectionIterations
- selectionInterval
- inversionSecs
- inversionNumSolutionAverages
- inversionThreadsPerSelector
- inversionAveragingEnabled
- inversionAveragingIterations
- inversionAveragingIntervalSecs

## Generic Inversion Execution

Iteration will run until at least one `completion criteria` is met. Iteration is broken up into `rounds`. 

At each round, iteration splits up into a pre-determined number (`inversionThreadsPerSelector`) of threads that all independently iterate from the same start state. Each thread runs until one `sub-completion criteria` is met for that thread. The best result of all threads is used to seed the threads at the next round - or as the result of the overall iteration if at the end of a round a `completion criteria` is met.

### Completion Criteria

These criteria determine the overall length of the inversion. All three criteria can be set up, and the inversion will end when one of them is satisfied.
- `setInversionMinutes()` or `setInversionSeconds()` sets a minimum duration.
- `setIterationCompletionCriteria()` sets a minimum number of iterations.
- `setEnergyChangeCompletionCriteria()` sets an energy change condition.

### Sub Completion Criteria

The length of each `round` is controlled by a sub completion criteria. Only of them can be set.
- `setSelectionInterval()` minimum round duration.
- `setSelectionIterations()` the minimum number of iterations.

### Parallelism

The number of threads to use each round can be specified with `setNumThreadsPerSelector()`.

### Examples:
```Java
        // set completion criteria: 100 iterations
        runner.setIterationCompletionCriteria(200);
        // run a single thread at each round 
        runner.inversionThreadsPerSelector(1);
        // set sub completion criteria: 10 iterations
        runner.setSelectionIterations(10);
        // turn off averaging for this example
        runner.setInversionAveraging(false);
        runner.setNumSolutionAverages(1);
```

These settings will result in 20 rounds of 1 thread with 10 iterations.

```Java
        // set completion criteria: 20 iterations
        runner.setIterationCompletionCriteria(20);
        // run three threads at each round 
        runner.inversionThreadsPerSelector(3);
        // set sub completion criteria: 6 iterations
        runner.setSelectionIterations(6);
        // turn off averaging for this example
        runner.setInversionAveraging(false);
        runner.setNumSolutionAverages(1);
```

These settings will result in 4 rounds (6 * 4 >= 20). Each round will have 3 threads, each running 6 iterations.

Completion criteria can also be time based:

```java
        // completion criteria
        runner.setInversionMinutes(2);
        runner.setNumThreadsPerSelector(3);
        // sub completion criteria
        runner.setSelectionInterval(60);
        // disable averaging
        runner.setInversionAveraging(false);
        runner.setNumSolutionAverages(1);
```

This inversion runs for a minimum of 2 minutes with 2 rounds at a minimum of 1 minute each. On my machine, it ran for 2:02 minutes with 195,534,181 iterations. The iteration count is based on the maximum iterations of the 3 threads of each round. Note that this only counts the iterations for one thread in each round. This is consistent with the previous example. For my example run, in the first round, the maximum iterations a thread achieved was 97,074,670, and in the second round it was 98,459,511 resulting in 195,534,181. 


-> TODO averaging



# Scenarios

## Repeatable Runs

Calling `setRepeatable(true)` on an inversion runner will do the following:
- Disable time-base completion criteria and time-based sub completion criteria
- Prevent multithreading during iteration and averaging
- Set the random seed to 1

Make sure you set deterministic completion criteria. For example:

```Java
        runner.setRepeatable(true);
        runner.setIterationCompletionCriteria(100);
        runner.setSelectionIterations(10);
```


# Oddness:

- subCompletionCriteria are exclusive, while completionCriteria can all be set up at the same time
