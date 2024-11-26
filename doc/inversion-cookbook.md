# Inversion Cookbook

## Generic Inversion Execution

Iteration will run until at least one `completion criteria` is met. Iteration is broken up into `rounds`. 

At each round, iteration splits up into a pre-determined number of threads that all independently iterate from the same start state. Each thread runs until one `sub completion criteria` is met for that thread. The best result of all threads is used to seed the threads at the next round - or as the result of the overall iteration if at the end of a round a `completion criteria` is met.

```
repeat until completion criteria {
   take best result of threads {
        repeat until sub completion criteria {
            iterate, take best result            
        }
    }
}
```

### Completion Criteria

These criteria determine the overall length of the inversion. All three criteria can be set up, and the inversion will end when one of them is satisfied at the beginning of a round.
- `setInversionMinutes()` or `setInversionSeconds()` sets a minimum duration.
- `setIterationCompletionCriteria()` sets a minimum number of iterations.
- `setEnergyChangeCompletionCriteria()` sets an energy change condition.

### Sub Completion Criteria

The length of each `round` is controlled by a sub completion criteria. Only one of them can be set.
- `setSelectionInterval()` minimum round duration.
- `setSelectionIterations()` the minimum number of iterations.

### Parallelism

The number of threads to use each round can be specified with `setNumThreadsPerSelector()`.

### Examples:
```Java
        // set completion criteria: 200 iterations
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

```
repeat until <200 iterations> {
   take best result of <1> threads {
        repeat until <10 iterations> {
            iterate, take best result            
        }
    }
}
```

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


## Averaging

Averaging runs multiple copies of the inversion rounds in parallel and averages the results. The execution structure is now:

```
repeat until completion criteria {
    average the best result of threads {
        repeat until average completion criteria {
            take best result of threads {
                repeat until sub completion criteria {
                    iterate, take best result            
                }
            }
        }
    }
}
```

Example:

```Java
        // generic behaviour, see completion criteria section
        runner.setInversionMinutes(5);
        runner.setNumThreadsPerSelector(3);
        runner.setSelectionInterval(30);
        // set up averaging
        runner.setInversionAveraging(true);
        runner.setNumSolutionAverages(5);
        runner.setInversionAveragingIntervalSecs(100);
```

The first three lines of this example set up completion criteria as in the time-based completion criteria example: Each round is 1 minute long and comprises three threads. The overall inversion is 2 minutes long.

The last three lines set up averaging with 5 threads that run for 100 seconds each.

This is what the structure looks like:

```
repeat until <5 minutes> {
    average the best result of <5> threads {
        repeat until <100 seconds> {
            take best result of <3> threads {
                repeat until <30 seconds> {
                    iterate, take best result            
                }
            }
        }
    }
}
```

## Scenarios

### Repeatable Runs

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

### Log Inversion State

`setEnableInversionStateLogging()` enables detailed logging of inversion state values for each round.

If you want to log at each iteration step, make each round exactly one iteration long:

```Java
        runner.setIterationCompletionCriteria(10);
        runner.setSelectionIterations(1);
        runner.setRepeatable(true);
        // log state to /tmp/stateLog
        runner.setEnableInversionStateLogging("/tmp/stateLog/");
        runner.setInversionAveraging(false);
```


# Oddness:

- subCompletionCriteria are exclusive, while completionCriteria can all be set up at the same time
- inconsistent naming of completionCriteria functions
  - `setInversionMinutes()` vs `setSelectionInterval()`
  - `setInversionMinutes()` vs `setIterationCompletionCriteria()`
- turning averaging off doesn't prevent `inversionNumSolutionAverages` from influencing thread count
- 
