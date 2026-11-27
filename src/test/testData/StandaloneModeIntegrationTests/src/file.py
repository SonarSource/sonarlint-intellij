def foo(a):  # NonCompliant
    b = 12
    if a == 1:
        return b
    return b
