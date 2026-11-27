#include <iostream>
#include "myincludes.h"

int main() {
    std::cout << "Hello, World!" << std::endl;
    foo();
    return 0;
}

void fun() {
#define N 11
    static const int s[] = { [1 << N] = 0 };
}
