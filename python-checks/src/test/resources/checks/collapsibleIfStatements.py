if a:
  if b:
    pass
  if c:
    pass

if a:
  if b:
    pass
  else:
    pass

if a:
#^[sc=1;ec=2]>
  if b: # Noncompliant {{Merge this if statement with the enclosing one.}}
# ^^
    pass

if a:
  pass
elif b:
  if c: # Noncompliant
    pass

if a:
  pass
elif b:
  if c:
    pass
else:
  pass

if a:
  if b: # Noncompliant
    if c: # Noncompliant
      pass

if a:
  if b: # Noncompliant
    if c:
      pass
    else:
      pass

if a:
  if b:
    pass
  elif c:
    pass

if a:
  if b:
    pass
  do_something()
