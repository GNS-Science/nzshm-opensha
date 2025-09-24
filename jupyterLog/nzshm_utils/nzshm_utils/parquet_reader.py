import pyarrow.parquet as pq
import numpy as np
import concurrent.futures

# groups with processses

g_parquet_file = None
g_col_name = None
g_n_result = None
g_kernel = None

# used by parquet_iterator_parallel to process a row group
def read_group_double_row(index):
  global g_parquet_file, g_col_name, g_n_result, g_kernel
  group = g_parquet_file.read_row_group(index).to_pandas()[g_col_name]
  result = np.zeros(shape=(g_n_result, len(group)-1))
  first = None
  previous = None
  index = -1
  for row in group:
    if len(row) == 0:
      continue
    if index >= 0:
      result[:,index] = g_kernel(previous, row)
    else:
      first = row
    previous = row
    index+=1 
  return (first, result, previous)

# used by parquet_iterator_parallel to init new processes
def init_fn(file_name, col_name, kernel, n_result):
  global g_parquet_file, g_col_name, g_n_result, g_kernel
  g_parquet_file = pq.ParquetFile(file_name)
  g_col_name = col_name
  g_n_result = n_result
  g_kernel = kernel
  


def parquet_iterator_parallel(file_name, col_name, kernel, n_result):
  # entry point
  # 
  # file_name: parquet file name
  # col_name: the column of interest
  # kernel: a function that takes two numpy arrays and returns a tuple of n_result length
  # n_result: number of values expected from the kernel function
  parquet_file = pq.ParquetFile(file_name)
  with concurrent.futures.ProcessPoolExecutor(initializer=init_fn, initargs=(file_name, col_name, kernel, n_result)) as executor:
    executor_result = executor.map(read_group_double_row, range(parquet_file.metadata.num_row_groups))
    
  prev = None
  filled = []    
  for (first_row, computed, last_row) in executor_result:
    if prev is not None:
      result = np.zeros(shape=(n_result, 1))
      result[:,0] = kernel(prev, first_row)
      filled.append(result)
    if computed.size > 0:
      filled.append(computed)
    prev = last_row
  result = []
  for index in range(n_result):
    result.append(np.concat([item[index] for item in filled]))
  
  return result
 