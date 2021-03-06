package config

import sorcer.arithmetic.provider.Adder
import sorcer.arithmetic.provider.Multiplier
import sorcer.arithmetic.provider.Subtractor
import static sorcer.eo.operator.*;
import sorcer.service.*;

String arg = "arg", result = "result";
String x1 = "x1", x2 = "x2", y = "y";

Task f3 = task("f3", sig("subtract", Subtractor.class),
   context("subtract", input(path(arg, x1), null), input(path(arg, x2), null),
	  output(path(result, y), null)));

Task f4 = task("f4", sig("multiply", Multiplier.class),
		   context("multiply", input(path(arg, x1), 10.0d), input(path(arg, x2), 50.0d),
			  output(path(result, y), null)));

Task f5 = task("f5", sig("add", Adder.class),
   context("add", input(path(arg, x1), 20.0d), input(path(arg, x2), 80.0d),
	  output(path(result, y), null)));

// Function Composition f3(f4(x1, x2), f5(x1, x2), f3(x1, x2))
Job f1 = job("f1", f4, f5, f3,
   pipe(output(f4, path(result, y)), input(f3, path(arg, x1))),
   pipe(output(f5, path(result, y)), input(f3, path(arg, x2))));

return f1;