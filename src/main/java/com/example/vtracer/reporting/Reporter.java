package com.example.vtracer.reporting;

import com.example.vtracer.analysis.calltree.CallTreeAnalyzer.CallTree;
import java.util.concurrent.CompletableFuture;

/** Interface for reporting call tree results */
public interface Reporter {

  /** Report asynchronously (non-blocking) */
  CompletableFuture<Void> reportAsync(CallTree tree);

  /** Report synchronously (blocking) */
  void reportSync(CallTree tree);
}
