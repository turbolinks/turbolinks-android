require 'sinatra'
require 'turbolinks/source'

module TurbolinksDemo
  class App < Sinatra::Base
    get '/' do
      @title = 'Demo'
      erb :index, layout: :layout
    end

    get '/one' do
      @title = 'Page One'
      erb :one, layout: :layout
    end

    get '/two' do
      @title = 'Page Two'
      erb :two, layout: :layout
    end

    get '/slow' do
      sleep 2
      @title = 'Slow'
      erb :slow, layout: :layout
    end

    get '/error' do
      @title = 'Error'
      erb :error, layout: :layout
    end

    get '/turbolinks.js' do
      send_file(Turbolinks::Source.asset_path + '/turbolinks.js')
    end
  end
end
