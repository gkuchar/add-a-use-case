// UC-STU-deactivate-student: the Deactivate button and its confirm dialog. The student API calls are stubbed, so
// these tests check what the page sends and shows; the backend's own tests cover who may deactivate and what changes.
// Run against the dev stack (backend + `npm run dev`) with `npm run cy:open`.

const activeJohn = {
  id: 4,
  username: 'j.smith@abc.edu',
  firstName: 'John',
  lastName: 'Smith',
  email: 'j.smith@abc.edu',
  enabled: true,
  roles: 'student',
  sectionId: 2,
  sectionName: '2023-2024',
  teamId: 1,
  teamName: 'Team1'
}

const deactivatedJohn = { ...activeJohn, enabled: false, teamId: null, teamName: null }

const found = (student) => ({
  flag: true,
  code: 200,
  message: 'Find student successfully',
  data: student
})

describe('Deactivate a student', () => {
  beforeEach(() => {
    cy.login('b.gates@abc.edu', '123456')
    cy.intercept('PATCH', '**/api/v1/students/4/deactivate', {
      body: {
        flag: true,
        code: 200,
        message: 'Deactivate student successfully',
        data: deactivatedJohn
      }
    }).as('deactivate')
  })

  it('sends nothing when the confirm dialog is cancelled (5a)', () => {
    cy.intercept('GET', '**/api/v1/students/4', { body: found(activeJohn) }).as('student')
    cy.visit('/students/4')
    cy.wait('@student')

    cy.get('[data-cy=deactivate-student]').click()
    cy.get('.el-message-box').should('contain', 'Deactivate John Smith?')
    cy.get('.el-message-box').contains('button', 'Cancel').click()

    cy.get('.el-message-box').should('not.exist')
    cy.get('[data-cy=student-status]').should('contain', 'Active')
    cy.get('@deactivate.all').should('have.length', 0)
  })

  it('sends one PATCH on confirm, shows the message, and hides the button afterwards (step 6)', () => {
    let reads = 0
    cy.intercept('GET', '**/api/v1/students/4', (req) => {
      reads += 1
      req.reply({ body: found(reads === 1 ? activeJohn : deactivatedJohn) })
    }).as('student')
    cy.visit('/students/4')
    cy.wait('@student')

    cy.get('[data-cy=deactivate-student]').click()
    cy.get('.el-message-box').should('contain', 'will be removed from their team')
    cy.get('.el-message-box').contains('button', 'Deactivate').click()

    cy.wait('@deactivate')
    cy.get('.el-message').should('contain', 'Deactivate student successfully')
    cy.get('@deactivate.all').should('have.length', 1)
    cy.get('[data-cy=student-status]').should('contain', 'Deactivated')
    cy.get('[data-cy=student-team]').should('contain', 'None')
    cy.get('[data-cy=deactivate-student]').should('not.exist')
  })

  it('shows the already-deactivated message when someone else got there first (6a)', () => {
    cy.intercept('GET', '**/api/v1/students/4', { body: found(activeJohn) }).as('student')
    cy.intercept('PATCH', '**/api/v1/students/4/deactivate', {
      body: {
        flag: true,
        code: 200,
        message: 'This student is already deactivated.',
        data: deactivatedJohn
      }
    }).as('deactivate')
    cy.visit('/students/4')
    cy.wait('@student')

    cy.get('[data-cy=deactivate-student]').click()
    cy.get('.el-message-box').contains('button', 'Deactivate').click()

    cy.wait('@deactivate')
    cy.get('.el-message').should('contain', 'This student is already deactivated.')
  })

  it('shows no Deactivate button for a deactivated student', () => {
    cy.intercept('GET', '**/api/v1/students/4', { body: found(deactivatedJohn) }).as('student')
    cy.visit('/students/4')
    cy.wait('@student')

    cy.get('[data-cy=student-status]').should('contain', 'Deactivated')
    cy.get('[data-cy=deactivate-student]').should('not.exist')
  })
})
