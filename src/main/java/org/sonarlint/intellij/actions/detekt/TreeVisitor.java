/*
 * SonarSource SLang
 * Copyright (C) 2018-2019 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarlint.intellij.actions.detekt;


import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

public class TreeVisitor<C extends TreeContext> {

  private List<ConsumerFilter<C, ?>> consumers;

  public TreeVisitor() {
    consumers = null;
  }

  public void scan(C ctx, @Nullable Tree root) {
    if (root != null) {
      if (ctx !=null) {
        ctx.before(root);
      }
      before(ctx, root);
      visit(ctx, root);
      after(ctx, root);
    }
  }
 
  private void visit(C ctx, @Nullable Tree node) {
    if (node != null) {
      if (ctx != null) {
        ctx.enter(node);
      }
      if (consumers != null) {
        for (ConsumerFilter<C, ?> consumer : consumers) {
          consumer.accept(ctx, node);
        }
      }
      node.children().forEach(child -> visit(ctx, child));
      if (ctx != null) {
        ctx.leave(node);
      }
    }
  }

  protected void before(C ctx, Tree root) {
    // default behaviour is to do nothing
  }

  protected void after(C ctx, Tree root) {
    // default behaviour is to do nothing
  }

  public <T extends Tree> TreeVisitor<C> register(Class<T> cls, BiConsumer<C, T> visitor) {
    if (consumers == null) {
      consumers = new ArrayList<>();
    }
    consumers.add(new ConsumerFilter<>(cls, visitor));
    return this;
  }

  private static class ConsumerFilter<C extends TreeContext, T extends Tree> {

    private final Class<T> cls;

    private final BiConsumer<C, T> delegate;

    private ConsumerFilter(Class<T> cls, BiConsumer<C, T> delegate) {
      this.cls = cls;
      this.delegate = delegate;
    }

    private void accept(C ctx, Tree node) {
      if (cls.isAssignableFrom(node.getClass())) {
        delegate.accept(ctx, cls.cast(node));
      }
    }

  }

}
