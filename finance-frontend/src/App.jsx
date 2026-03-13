import Navbar from './components/Navbar'
import Hero from './components/Hero'
import About from './components/About'
import Footer from './components/Footer'

export default function App() {
  return (
    <div className="min-h-screen bg-[#0a0e1a]">
      <Navbar />
      <Hero />
      <About />
      <Footer />
    </div>
  )
}
