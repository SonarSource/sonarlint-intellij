# frozen_string_literal: true

class File
  def foo()
    prepare('action random1')
    execute('action random1')
    release('action random1')
  end
end
