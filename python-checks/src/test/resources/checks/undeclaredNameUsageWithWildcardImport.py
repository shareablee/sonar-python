from undeclaredNameUsageImported import *

def f():
    print(a) # OK, mod exports symbol a
    print(c) # Noncompliant
